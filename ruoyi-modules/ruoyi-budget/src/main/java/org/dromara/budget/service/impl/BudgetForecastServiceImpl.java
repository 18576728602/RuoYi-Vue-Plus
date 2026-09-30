package org.dromara.budget.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.BudgetData;
import org.dromara.budget.domain.BudgetForecast;
import org.dromara.budget.domain.BudgetForecastItem;
import org.dromara.budget.domain.BudgetPlan;
import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.budget.domain.bo.BudgetForecastQuery;
import org.dromara.budget.domain.vo.BudgetForecastDetailVo;
import org.dromara.budget.domain.vo.BudgetForecastItemVo;
import org.dromara.budget.domain.vo.BudgetForecastVersionVo;
import org.dromara.budget.mapper.BudgetDataMapper;
import org.dromara.budget.mapper.BudgetForecastItemMapper;
import org.dromara.budget.mapper.BudgetForecastMapper;
import org.dromara.budget.mapper.BudgetPlanMapper;
import org.dromara.budget.mapper.BudgetTemplateItemMapper;
import org.dromara.budget.service.IBudgetForecastService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.domain.SysDept;
import org.dromara.system.mapper.SysDeptMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 滚动预测 Service实现（季度滚动，简单/趋势法）
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetForecastServiceImpl implements IBudgetForecastService {

    private final BudgetForecastMapper forecastMapper;
    private final BudgetForecastItemMapper forecastItemMapper;
    private final BudgetDataMapper budgetDataMapper;
    private final BudgetPlanMapper budgetPlanMapper;
    private final BudgetTemplateItemMapper budgetTemplateItemMapper;
    private final SysDeptMapper sysDeptMapper;

    // 刷新时点 → 已过季数（Q1=1,Q2=2,Q3=3,Q4=4,YTD=按剩余为YTD则只保证实际情况已覆盖）
    private static final Map<String, Integer> PASSED_QUARTER = Map.of(
        "Q1", 1, "Q2", 2, "Q3", 3, "Q4", 4, "YTD", 1
    );

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generateForecast(BudgetForecastQuery query) {
        Long planId = query.getPlanId();
        Long orgId = resolveScopeOrg(query.getOrgId());
        String refreshType = normalizeRefreshType(query.getRefreshType());
        String method = StrUtil.isNotBlank(query.getForecastMethod()) ? query.getForecastMethod() : "TREND";
        if (!"SIMPLE".equals(method) && !"TREND".equals(method)) {
            method = "TREND";
        }

        BudgetPlan plan = budgetPlanMapper.selectById(planId);
        if (plan == null) {
            throw new ServiceException("预算方案不存在");
        }
        SysDept dept = sysDeptMapper.selectById(orgId);
        if (dept == null) {
            throw new ServiceException("填报单位不存在");
        }

        int passed = PASSED_QUARTER.getOrDefault(refreshType, 1);

        // 读取该方案+单位的预算与季度实际数据
        List<BudgetData> dataList = budgetDataMapper.selectList(new LambdaQueryWrapper<BudgetData>()
            .eq(BudgetData::getPlanId, planId)
            .eq(BudgetData::getDeptId, orgId));
        if (CollUtil.isEmpty(dataList)) {
            throw new ServiceException("该单位无预算数据，无法生成滚动预测");
        }
        Map<String, BudgetData> dataMap = dataList.stream()
            .filter(d -> StrUtil.isNotBlank(d.getItemCode()))
            .collect(Collectors.toMap(
                d -> (StrUtil.blankToDefault(d.getTemplateCode(), "")) + "_" + d.getItemCode(),
                d -> d, (a, b) -> a));

        // 生成版本号
        String forecastNo = buildForecastNo(plan.getBudgetYear(), refreshType, planId, orgId);

        // 覆盖旧 ACTIVE 版本
        forecastMapper.update(null, new LambdaQueryWrapper<BudgetForecast>()
            .eq(BudgetForecast::getPlanId, planId)
            .eq(BudgetForecast::getOrgId, orgId)
            .eq(BudgetForecast::getStatus, "ACTIVE"));

        BudgetForecast forecast = new BudgetForecast();
        forecast.setPlanId(planId);
        forecast.setOrgId(orgId);
        forecast.setForecastNo(forecastNo);
        forecast.setRefreshType(refreshType);
        forecast.setRefreshDate(new Date());
        forecast.setForecastMethod(method);
        forecast.setOperatorId(LoginHelper.getUserId());
        forecast.setOperatorName(LoginHelper.getUsername());
        forecast.setStatus("ACTIVE");
        forecastMapper.insert(forecast);

        // 条件：本方案该单位下需要预测的科目（可编辑明细，排除汇总由后端按层级计算，但为简化按预算数据行铺开）
        List<BudgetForecastItem> items = new ArrayList<>();
        for (BudgetData d : dataList) {
            if (StrUtil.isBlank(d.getItemCode())) {
                continue;
            }
            BigDecimal budget = nz(d.getBudgetAmount());
            BigDecimal q1 = nz(d.getQ1Amount());
            BigDecimal q2 = nz(d.getQ2Amount());
            BigDecimal q3 = nz(d.getQ3Amount());
            BigDecimal q4 = nz(d.getQ4Amount());
            BigDecimal actualTotal = q1.add(q2).add(q3).add(q4);

            // 已实现季度实际（作为预测基线）
            BigDecimal[] actualByQ = {q1, q2, q3, q4};
            // 预测各季度
            BigDecimal[] foreByQ = {q1, q2, q3, q4};
            if (passed < 4) {
                BigDecimal[] pred = predictRemaining(budget, actualByQ, passed, method);
                for (int i = passed; i < 4; i++) {
                    // i>=passed 的季度用预测（含当前已部分适当处理：直接给预测值）
                    foreByQ[i] = pred[i - passed];
                }
            }
            BigDecimal forecastTotal = foreByQ[0].add(foreByQ[1]).add(foreByQ[2]).add(foreByQ[3]);

            BudgetForecastItem item = new BudgetForecastItem();
            item.setForecastId(forecast.getId());
            item.setPlanId(planId);
            item.setOrgId(orgId);
            item.setTemplateCode(d.getTemplateCode());
            item.setItemCode(d.getItemCode());
            item.setParentCode(null);
            item.setBudgetAmount(budget);
            item.setActualQ1(q1);
            item.setActualQ2(q2);
            item.setActualQ3(q3);
            item.setActualQ4(q4);
            item.setActualTotal(actualTotal);
            item.setForecastQ1(foreByQ[0]);
            item.setForecastQ2(foreByQ[1]);
            item.setForecastQ3(foreByQ[2]);
            item.setForecastQ4(foreByQ[3]);
            item.setForecastTotal(forecastTotal);
            item.setDeviation(forecastTotal.subtract(budget));
            items.add(item);
        }

        // 批量插入明细
        items.forEach(forecastItemMapper::insert);
        return forecast.getId();
    }

    @Override
    public List<BudgetForecastVersionVo> listVersions(Long planId, Long orgId) {
        orgId = resolveScopeOrg(orgId);
        List<BudgetForecast> list = forecastMapper.selectList(new LambdaQueryWrapper<BudgetForecast>()
            .eq(BudgetForecast::getPlanId, planId)
            .eq(BudgetForecast::getOrgId, orgId)
            .orderByDesc(BudgetForecast::getRefreshDate));
        List<BudgetForecastVersionVo> voList = new ArrayList<>();
        for (BudgetForecast f : list) {
            BudgetForecastVersionVo vo = new BudgetForecastVersionVo();
            vo.setId(f.getId());
            vo.setPlanId(f.getPlanId());
            vo.setOrgId(f.getOrgId());
            vo.setForecastNo(f.getForecastNo());
            vo.setRefreshType(f.getRefreshType());
            vo.setRefreshDate(f.getRefreshDate());
            vo.setForecastMethod(f.getForecastMethod());
            vo.setRemark(f.getRemark());
            vo.setOperatorName(f.getOperatorName());
            vo.setStatus(f.getStatus());
            voList.add(vo);
        }
        return voList;
    }

    @Override
    public BudgetForecastDetailVo getForecastDetail(Long forecastId) {
        BudgetForecast f = forecastMapper.selectById(forecastId);
        if (f == null) {
            throw new ServiceException("预测版本不存在");
        }
        BudgetPlan plan = budgetPlanMapper.selectById(f.getPlanId());
        SysDept dept = sysDeptMapper.selectById(f.getOrgId());

        List<BudgetForecastItem> fItems = forecastItemMapper.selectList(
            new LambdaQueryWrapper<BudgetForecastItem>()
                .eq(BudgetForecastItem::getForecastId, forecastId));

        BudgetForecastDetailVo vo = new BudgetForecastDetailVo();
        vo.setId(f.getId());
        vo.setPlanId(f.getPlanId());
        vo.setPlanName(plan != null ? plan.getPlanName() : "");
        vo.setOrgId(f.getOrgId());
        vo.setOrgName(dept != null ? dept.getDeptName() : "");
        vo.setForecastNo(f.getForecastNo());
        vo.setRefreshType(f.getRefreshType());
        vo.setForecastMethod(f.getForecastMethod());

        // 尝试补科目名称（预算数据行本身无名称，从模板科目表查询）
        Map<String, String> nameMap = new HashMap<>();
        List<BudgetTemplateItem> tplItems = budgetTemplateItemMapper.selectList(
            new LambdaQueryWrapper<BudgetTemplateItem>()
                .eq(BudgetTemplateItem::getPlanId, f.getPlanId()));
        for (BudgetTemplateItem t : tplItems) {
            if (StrUtil.isNotBlank(t.getItemCode())) {
                nameMap.putIfAbsent(t.getItemCode(), t.getItemName());
            }
        }

        BigDecimal totalBudget = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;
        BigDecimal totalForecast = BigDecimal.ZERO;
        List<BudgetForecastItemVo> itemVos = new ArrayList<>();
        for (BudgetForecastItem it : fItems) {
            BudgetForecastItemVo iv = new BudgetForecastItemVo();
            iv.setId(it.getId());
            iv.setTemplateCode(it.getTemplateCode());
            iv.setItemCode(it.getItemCode());
            iv.setItemName(StrUtil.isNotBlank(it.getItemName())
                ? it.getItemName()
                : nameMap.getOrDefault(it.getItemCode(), it.getItemCode()));
            iv.setBudgetAmount(it.getBudgetAmount());
            iv.setActualQ1(it.getActualQ1());
            iv.setActualQ2(it.getActualQ2());
            iv.setActualQ3(it.getActualQ3());
            iv.setActualQ4(it.getActualQ4());
            iv.setActualTotal(it.getActualTotal());
            iv.setForecastQ1(it.getForecastQ1());
            iv.setForecastQ2(it.getForecastQ2());
            iv.setForecastQ3(it.getForecastQ3());
            iv.setForecastQ4(it.getForecastQ4());
            iv.setForecastTotal(it.getForecastTotal());
            iv.setDeviation(it.getDeviation());
            iv.setForecastRate(rate(it.getForecastTotal(), it.getBudgetAmount()));
            iv.setActualRate(rate(it.getActualTotal(), it.getBudgetAmount()));
            itemVos.add(iv);

            totalBudget = totalBudget.add(nz(it.getBudgetAmount()));
            totalActual = totalActual.add(nz(it.getActualTotal()));
            totalForecast = totalForecast.add(nz(it.getForecastTotal()));
        }

        vo.setTotalBudget(totalBudget);
        vo.setTotalActual(totalActual);
        vo.setTotalForecast(totalForecast);
        vo.setTotalDeviation(totalForecast.subtract(totalBudget));
        vo.setForecastRate(rate(totalForecast, totalBudget));
        vo.setItems(itemVos);

        // 趋势图：按季度累计（预算线/实际线/预测线）
        int passed = PASSED_QUARTER.getOrDefault(f.getRefreshType(), 1);
        List<String> quarters = Arrays.asList("Q1", "Q2", "Q3", "Q4");
        List<BigDecimal> bLine = new ArrayList<>();
        List<BigDecimal> aLine = new ArrayList<>();
        List<BigDecimal> fLine = new ArrayList<>();
        BigDecimal bRun = BigDecimal.ZERO;
        BigDecimal aRun = BigDecimal.ZERO;
        BigDecimal fRun = BigDecimal.ZERO;
        for (int i = 0; i < 4; i++) {
            bRun = bRun.add(budgetShare(fItems, i));
            aRun = aRun.add(quarterlyActual(fItems, i));
            fRun = fRun.add(quarterlyForecast(fItems, i));
            bLine.add(round(bRun));
            aLine.add(round(aRun));
            fLine.add(round(fRun));
        }
        vo.setQuarters(quarters);
        vo.setBudgetLine(bLine);
        vo.setActualLine(aLine);
        vo.setForecastLine(fLine);
        return vo;
    }

    @Override
    public void deleteForecast(Long forecastId) {
        BudgetForecast f = forecastMapper.selectById(forecastId);
        if (f == null) {
            return;
        }
        forecastMapper.deleteById(forecastId);
        forecastItemMapper.delete(new LambdaQueryWrapper<BudgetForecastItem>()
            .eq(BudgetForecastItem::getForecastId, forecastId));
    }

    // ============ 预测算法 ============

    /**
     * 剩余季度预测。[0]=预测剩余第1季，依次
     */
    private BigDecimal[] predictRemaining(BigDecimal budget, BigDecimal[] actualByQ, int passed, String method) {
        BigDecimal[] out = new BigDecimal[4 - passed];
        int remaining = 4 - passed;
        BigDecimal actualTotal = BigDecimal.ZERO;
        for (int i = 0; i < passed; i++) {
            actualTotal = actualTotal.add(nz(actualByQ[i]));
        }

        if ("SIMPLE".equals(method)) {
            // 简单法：预算均值/季（全年预算等分到剩余季度，不足补已执行）
            BigDecimal qAvg = budget.divide(new BigDecimal("4"), 2, RoundingMode.HALF_UP);
            for (int i = 0; i < remaining; i++) {
                out[i] = qAvg;
            }
            // 均衡调整：若已执行远超均值则上调剩余，反之下调 → 用均值保持平稳
        } else {
            // 趋势法：按已执行进度推算全年，再按剩余季均分
            BigDecimal pace = budget.compareTo(BigDecimal.ZERO) > 0
                ? actualTotal.divide(budget, 4, RoundingMode.HALF_UP)
                : BigDecimal.ONE;
            // 全年预测 ≈ 已执行/已过季率 = actualTotal / (passed/4)
            if (passed > 0) {
                BigDecimal annualPred = actualTotal.multiply(new BigDecimal("4"))
                    .divide(new BigDecimal(passed), 2, RoundingMode.HALF_UP);
                // 兜底：不高于预算2倍，不低于0
                if (annualPred.compareTo(budget.multiply(new BigDecimal("2"))) > 0) {
                    annualPred = budget.multiply(new BigDecimal("2"));
                }
                BigDecimal remainTotal = annualPred.subtract(actualTotal);
                if (remainTotal.compareTo(BigDecimal.ZERO) < 0) {
                    remainTotal = BigDecimal.ZERO;
                }
                BigDecimal perRemain = remainTotal.divide(new BigDecimal(remaining), 2, RoundingMode.HALF_UP);
                for (int i = 0; i < remaining; i++) {
                    out[i] = perRemain;
                }
            } else {
                BigDecimal qAvg = budget.divide(new BigDecimal("4"), 2, RoundingMode.HALF_UP);
                for (int i = 0; i < remaining; i++) {
                    out[i] = qAvg;
                }
            }
        }
        return out;
    }

    // ============ 工具 ============

    private BigDecimal quarterlyForecast(List<BudgetForecastItem> items, int qi) {
        BigDecimal s = BigDecimal.ZERO;
        for (BudgetForecastItem it : items) {
            if (qi == 0) s = s.add(nz(it.getForecastQ1()));
            else if (qi == 1) s = s.add(nz(it.getForecastQ2()));
            else if (qi == 2) s = s.add(nz(it.getForecastQ3()));
            else s = s.add(nz(it.getForecastQ4()));
        }
        return s;
    }

    private BigDecimal quarterlyActual(List<BudgetForecastItem> items, int qi) {
        BigDecimal s = BigDecimal.ZERO;
        for (BudgetForecastItem it : items) {
            if (qi == 0) s = s.add(nz(it.getActualQ1()));
            else if (qi == 1) s = s.add(nz(it.getActualQ2()));
            else if (qi == 2) s = s.add(nz(it.getActualQ3()));
            else s = s.add(nz(it.getActualQ4()));
        }
        return s;
    }

    private BigDecimal budgetShare(List<BudgetForecastItem> items, int qi) {
        BigDecimal totalBudget = items.stream()
            .map(BudgetForecastItem::getBudgetAmount).filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalBudget.divide(new BigDecimal("4"), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(BudgetForecastItem it, BigDecimal a, BigDecimal b) {
        return nz(a).add(nz(b));
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO;
    }

    private BigDecimal round(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal rate(BigDecimal value, BigDecimal base) {
        if (base == null || base.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return value.multiply(new BigDecimal("100")).divide(base, 2, RoundingMode.HALF_UP);
    }

    private String normalizeRefreshType(String t) {
        if (StrUtil.isBlank(t)) {
            return "Q1";
        }
        String up = t.toUpperCase();
        if ("YTD".equals(up)) {
            return "YTD";
        }
        return up;
    }

    private String buildForecastNo(Long year, String refreshType, Long planId, Long orgId) {
        String base = "Q" ;
        // 版本号：2027Q1滚动版 / 2027滚动版
        int seq = (int) (forecastMapper.selectCount(new LambdaQueryWrapper<BudgetForecast>()
            .eq(BudgetForecast::getPlanId, planId)
            .eq(BudgetForecast::getOrgId, orgId)) + 1);
        if ("YTD".equals(refreshType)) {
            return (year != null ? year : "") + "年度滚动版" + seq;
        }
        return (year != null ? year : "") + refreshType + "滚动版" + seq;
    }

    private boolean isGlobalUser() {
        if (LoginHelper.isSuperAdmin() || LoginHelper.isTenantAdmin()) {
            return true;
        }
        try {
            List<org.dromara.common.core.domain.dto.RoleDTO> roles = LoginHelper.getLoginUser().getRoles();
            if (roles != null) {
                return roles.stream().anyMatch(r -> "1".equals(r.getDataScope()));
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private Long resolveScopeOrg(Long orgId) {
        if (isGlobalUser()) {
            return orgId;
        }
        return LoginHelper.getDeptId();
    }
}