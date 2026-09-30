package org.dromara.budget.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.BudgetAdjustment;
import org.dromara.budget.domain.BudgetData;
import org.dromara.budget.domain.BudgetPlan;
import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.budget.domain.BudgetWarningRecord;
import org.dromara.budget.domain.bo.BudgetWarningRecordBo;
import org.dromara.budget.mapper.BudgetAdjustmentMapper;
import org.dromara.budget.mapper.BudgetDataMapper;
import org.dromara.budget.mapper.BudgetPlanMapper;
import org.dromara.budget.mapper.BudgetTemplateItemMapper;
import org.dromara.budget.mapper.BudgetWarningRecordMapper;
import org.dromara.budget.service.IBudgetExecWarningService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.domain.SysDept;
import org.dromara.system.mapper.SysDeptMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 预算执行预警 Service实现（智能预警增强）
 *
 * <p>多源融合：在"执行率 vs 时间进度"三级预警基础上，融合预算调整(ADJUST)、敏感支出(SENSITIVE)
 * 信号，计算综合风险评分。并提供预警闭环管理(触发→处置→跟踪→升级→解除)，全部留痕。
 *
 * <p>等级规则：
 * 红色(RED)：累计执行 &gt; 预算金额（超预算）或综合评分≥90
 * 橙色(ORANGE)：执行率≥90%且超前于时间进度
 * 黄色(YELLOW)：执行率≥阈值(默认80)且超前于时间进度
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetExecWarningServiceImpl implements IBudgetExecWarningService {

    private final BudgetDataMapper budgetDataMapper;
    private final BudgetTemplateItemMapper templateItemMapper;
    private final BudgetPlanMapper budgetPlanMapper;
    private final BudgetAdjustmentMapper budgetAdjustmentMapper;
    private final BudgetWarningRecordMapper budgetWarningRecordMapper;
    private final SysDeptMapper sysDeptMapper;

    /** 敏感支出关键词（命中即标记 SENSITIVE 源） */
    private static final Set<String> SENSITIVE_KEYWORDS = new HashSet<>(Arrays.asList(
        "三公", "公务接待", "公务用车", "因公出国", "公费出国", "出国（境）", "出国(境)",
        "会议", "差旅", "招待", "礼品", "物业管理"));

    @Override
    public List<Map<String, Object>> getWarningList(Long planId, Long orgId, Integer threshold) {
        int th = (threshold == null || threshold < 1) ? 80 : threshold;

        // 1. 模板科目：排除01模板，排除汇总行
        LambdaQueryWrapper<BudgetTemplateItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(BudgetTemplateItem::getPlanId, 0L);
        itemWrapper.ne(BudgetTemplateItem::getTemplateCode, "B01");
        itemWrapper.orderByAsc(BudgetTemplateItem::getTemplateCode);
        itemWrapper.orderByAsc(BudgetTemplateItem::getItemCode);
        List<BudgetTemplateItem> items = templateItemMapper.selectList(itemWrapper);

        Map<String, BudgetTemplateItem> itemMap = new HashMap<>();
        Map<String, String> tplNameByCode = new LinkedHashMap<>();
        for (BudgetTemplateItem it : items) {
            itemMap.put(it.getItemCode(), it);
            tplNameByCode.putIfAbsent(it.getTemplateCode(), it.getTemplateName());
        }

        // 2. 方案名映射 + 年度时间进度
        Map<Long, String> planNameMap = new HashMap<>();
        Map<Long, BigDecimal> planTimeRateMap = new HashMap<>();
        List<BudgetPlan> plans = budgetPlanMapper.selectList(null);
        int nowMonth = java.time.LocalDate.now().getMonthValue();
        for (BudgetPlan p : plans) {
            planNameMap.put(p.getId(), p.getPlanName());
            planTimeRateMap.put(p.getId(), computeTimeRate(p, nowMonth));
        }

        // 3. 预算执行数据
        LambdaQueryWrapper<BudgetData> dataWrapper = new LambdaQueryWrapper<>();
        if (planId != null) {
            dataWrapper.eq(BudgetData::getPlanId, planId);
        }
        if (orgId != null) {
            dataWrapper.eq(BudgetData::getDeptId, orgId);
        }
        List<BudgetData> dataList = budgetDataMapper.selectList(dataWrapper);

        // 3.1 多源融合：已通过预算调整（按 组织+表+科目 聚合）
        List<BudgetAdjustment> adjustList = budgetAdjustmentMapper.selectList(
            new LambdaQueryWrapper<BudgetAdjustment>().eq(BudgetAdjustment::getStatus, "APPROVED"));
        // key: planId#orgId#templateCode#itemCode
        Map<String, AdjustmentSignal> adjustIndex = new HashMap<>();
        for (BudgetAdjustment a : adjustList) {
            String k = keyOf(a.getPlanId(), a.getOrgId(), a.getTemplateCode(), a.getItemCode());
            AdjustmentSignal s = adjustIndex.computeIfAbsent(k, x -> new AdjustmentSignal());
            s.count++;
            if (a.getAdjustAmount() != null) {
                s.total = s.total.add(a.getAdjustAmount());
            }
        }

        // 4. 单位名映射
        Set<Long> deptIds = dataList.stream()
            .map(BudgetData::getDeptId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, String> deptNameMap = new HashMap<>();
        for (Long did : deptIds) {
            SysDept d = sysDeptMapper.selectById(did);
            if (d != null) {
                deptNameMap.put(did, d.getDeptName());
            }
        }

        // 5. 逐条判断预警（多源融合）
        List<Map<String, Object>> result = new ArrayList<>();
        for (BudgetData d : dataList) {
            if (d.getItemCode() == null || d.getTemplateCode() == null) continue;
            if ("B01".equals(d.getTemplateCode())) continue;
            BudgetTemplateItem item = itemMap.get(d.getItemCode());
            if (item == null) continue;
            if (item.getIsSummary() != null && item.getIsSummary() == 1) continue;

            BigDecimal budget = d.getBudgetAmount();
            if (budget == null || budget.signum() <= 0) continue;

            BigDecimal exec = sumExec(d);
            if (exec == null || exec.signum() <= 0) continue;

            BigDecimal rate = exec.multiply(BigDecimal.valueOf(100))
                .divide(budget, 2, RoundingMode.HALF_UP);

            // 时间进度（该方案已过月份比例，最多100%）
            BigDecimal timeRate = planTimeRateMap.getOrDefault(d.getPlanId(), BigDecimal.valueOf(100));
            // 执行率显著高于时间进度（执行超前）
            boolean aheadOfSchedule = rate.compareTo(timeRate) > 0;

            boolean overrun = exec.compareTo(budget) > 0;

            // 多源融合信号
            AdjustmentSignal adj = adjustIndex.get(keyOf(d.getPlanId(), d.getDeptId(), d.getTemplateCode(), d.getItemCode()));
            boolean hasAdjust = adj != null && adj.count > 0;
            boolean sensitive = isSensitive(d.getTemplateCode(), item.getItemName());

            // 综合风险评分
            BigDecimal riskScore = computeRiskScore(rate, overrun, hasAdjust, sensitive);

            // 三级预警（融合调整后的最终等级）
            String level;
            if (overrun || riskScore.compareTo(BigDecimal.valueOf(90)) >= 0) {
                level = "RED";
            } else if (rate.compareTo(BigDecimal.valueOf(90)) >= 0 && aheadOfSchedule) {
                level = "ORANGE";
            } else if (rate.compareTo(BigDecimal.valueOf(th)) >= 0 && aheadOfSchedule) {
                level = "YELLOW";
            } else {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("planId", d.getPlanId());
            row.put("planName", planNameMap.getOrDefault(d.getPlanId(), ""));
            row.put("orgId", d.getDeptId());
            row.put("orgName", deptNameMap.getOrDefault(d.getDeptId(), ""));
            row.put("templateCode", d.getTemplateCode());
            row.put("templateName", tplNameByCode.getOrDefault(d.getTemplateCode(), d.getTemplateCode()));
            row.put("itemCode", d.getItemCode());
            row.put("itemName", item.getItemName());
            row.put("budgetAmount", budget);
            row.put("execAmount", exec);
            row.put("execRate", rate);
            row.put("timeRate", timeRate);
            row.put("diff", exec.subtract(budget));
            row.put("level", level);
            // 多源融合信息
            row.put("sources", sourceLabels(overrun, hasAdjust, sensitive));
            row.put("riskScore", riskScore);
            row.put("adjustCount", hasAdjust ? adj.count : 0);
            row.put("adjustTotal", hasAdjust ? adj.total : BigDecimal.ZERO);
            result.add(row);
        }

        // 按方案、等级排序，红色靠前（RED<ORANGE<YELLOW），同级按风险评分降序
        Map<String, Integer> levelRank = new HashMap<>();
        levelRank.put("RED", 0);
        levelRank.put("ORANGE", 1);
        levelRank.put("YELLOW", 2);
        result.sort((a, b) -> {
            int la = levelRank.getOrDefault(String.valueOf(a.get("level")), 9);
            int lb = levelRank.getOrDefault(String.valueOf(b.get("level")), 9);
            if (la != lb) return la - lb;
            BigDecimal ra = (BigDecimal) a.get("riskScore");
            BigDecimal rb = (BigDecimal) b.get("riskScore");
            int rscmp = rb.compareTo(ra);
            if (rscmp != 0) return rscmp;
            return String.valueOf(a.get("planName")).compareTo(String.valueOf(b.get("planName")));
        });
        return result;
    }

    @Override
    public List<Map<String, Object>> listRecords(Long planId, Long orgId, String level, String status) {
        LambdaQueryWrapper<BudgetWarningRecord> w = new LambdaQueryWrapper<>();
        if (planId != null) w.eq(BudgetWarningRecord::getPlanId, planId);
        if (orgId != null) w.eq(BudgetWarningRecord::getOrgId, orgId);
        if (level != null && !level.isEmpty()) w.eq(BudgetWarningRecord::getLevel, level);
        if (status != null && !status.isEmpty()) w.eq(BudgetWarningRecord::getStatus, status);
        w.orderByDesc(BudgetWarningRecord::getCreateTime);
        List<BudgetWarningRecord> recs = budgetWarningRecordMapper.selectList(w);

        Map<Long, String> deptNameMap = deptNameMap(recs.stream().map(BudgetWarningRecord::getOrgId)
            .filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, String> planNameMap = planNameMap(recs.stream().map(BudgetWarningRecord::getPlanId)
            .filter(Objects::nonNull).collect(Collectors.toSet()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (BudgetWarningRecord r : recs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("recordId", r.getRecordId());
            m.put("planId", r.getPlanId());
            m.put("planName", planNameMap.getOrDefault(r.getPlanId(), ""));
            m.put("orgId", r.getOrgId());
            m.put("orgName", deptNameMap.getOrDefault(r.getOrgId(), ""));
            m.put("templateCode", r.getTemplateCode());
            m.put("itemCode", r.getItemCode());
            m.put("level", r.getLevel());
            m.put("sources", r.getSources());
            m.put("riskScore", r.getRiskScore());
            m.put("execRate", r.getExecRate());
            m.put("status", r.getStatus());
            m.put("handlerId", r.getHandlerId());
            m.put("handlerName", r.getHandlerName());
            m.put("improvePlan", r.getImprovePlan());
            m.put("trackResult", r.getTrackResult());
            m.put("escalated", r.getEscalated());
            m.put("escalateTo", r.getEscalateTo());
            m.put("handleTime", r.getHandleTime());
            m.put("closeTime", r.getCloseTime());
            m.put("remark", r.getRemark());
            m.put("createdAt", r.getCreateTime());
            result.add(m);
        }
        return result;
    }

    @Override
    public Long createRecord(BudgetWarningRecordBo bo) {
        BudgetWarningRecord r = new BudgetWarningRecord();
        r.setPlanId(toLong(bo.getPlanId()));
        r.setOrgId(bo.getOrgId());
        r.setTemplateCode(bo.getTemplateCode());
        r.setItemCode(bo.getItemCode());
        r.setLevel(bo.getLevel());
        r.setSources(bo.getSources());
        r.setRiskScore(toBigDecimal(bo.getRiskScore()));
        r.setExecRate(toBigDecimal(bo.getExecRate()));
        r.setStatus(bo.getStatus() != null && !bo.getStatus().isEmpty() ? bo.getStatus() : "OPEN");
        r.setHandlerId(LoginHelper.getUserId());
        r.setHandlerName(firstNonBlank(bo.getHandlerName(), LoginHelper.getUsername()));
        r.setImprovePlan(bo.getImprovePlan());
        r.setTrackResult(bo.getTrackResult());
        r.setEscalated(bo.getEscalated() != null && bo.getEscalated() == 1 ? 1 : 0);
        r.setEscalateTo(bo.getEscalateTo());
        r.setRemark(bo.getRemark());
        if (r.getHandlerId() != null) {
            r.setHandleTime(new Date());
        }
        budgetWarningRecordMapper.insert(r);
        return r.getRecordId();
    }

    @Override
    public void handleRecord(BudgetWarningRecordBo bo) {
        if (bo.getRecordId() == null) {
            throw new IllegalArgumentException("预警记录ID不能为空");
        }
        BudgetWarningRecord r = budgetWarningRecordMapper.selectById(bo.getRecordId());
        if (r == null) {
            throw new IllegalArgumentException("预警记录不存在");
        }
        if (bo.getStatus() != null && !bo.getStatus().isEmpty()) {
            r.setStatus(bo.getStatus());
        }
        if (bo.getImprovePlan() != null) {
            r.setImprovePlan(bo.getImprovePlan());
        }
        if (bo.getTrackResult() != null) {
            r.setTrackResult(bo.getTrackResult());
        }
        if (bo.getRemark() != null) {
            r.setRemark(bo.getRemark());
        }
        r.setHandlerId(LoginHelper.getUserId());
        r.setHandlerName(firstNonBlank(bo.getHandlerName(), LoginHelper.getUsername()));
        r.setHandleTime(new Date());
        budgetWarningRecordMapper.updateById(r);
    }

    @Override
    public void escalateRecord(BudgetWarningRecordBo bo) {
        if (bo.getRecordId() == null) {
            throw new IllegalArgumentException("预警记录ID不能为空");
        }
        BudgetWarningRecord r = budgetWarningRecordMapper.selectById(bo.getRecordId());
        if (r == null) {
            throw new IllegalArgumentException("预警记录不存在");
        }
        r.setEscalated(1);
        r.setStatus("ESCALATED");
        if (bo.getEscalateTo() != null && !bo.getEscalateTo().isEmpty()) {
            r.setEscalateTo(bo.getEscalateTo());
        }
        if (bo.getImprovePlan() != null) {
            r.setImprovePlan(bo.getImprovePlan());
        }
        r.setHandlerId(LoginHelper.getUserId());
        r.setHandlerName(firstNonBlank(bo.getHandlerName(), LoginHelper.getUsername()));
        r.setHandleTime(new Date());
        budgetWarningRecordMapper.updateById(r);
    }

    @Override
    public void closeRecord(Long recordId, String result) {
        if (recordId == null) {
            throw new IllegalArgumentException("预警记录ID不能为空");
        }
        BudgetWarningRecord r = budgetWarningRecordMapper.selectById(recordId);
        if (r == null) {
            throw new IllegalArgumentException("预警记录不存在");
        }
        r.setStatus("CLOSED");
        r.setEscalated(0);
        r.setCloseTime(new Date());
        if (result != null && !result.isEmpty()) {
            r.setTrackResult(result);
        }
        budgetWarningRecordMapper.updateById(r);
    }

    // ==================== 多源融合辅助 ====================

    private boolean isSensitive(String templateCode, String itemName) {
        if ("B16".equals(templateCode)) {
            return true;
        }
        if (itemName == null) {
            return false;
        }
        for (String kw : SENSITIVE_KEYWORDS) {
            if (itemName.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal computeRiskScore(BigDecimal rate, boolean overrun, boolean hasAdjust, boolean sensitive) {
        // 基础分取执行率（超100按100计）
        BigDecimal score = rate.min(BigDecimal.valueOf(100));
        if (overrun) {
            score = score.add(BigDecimal.valueOf(15));
        }
        if (hasAdjust) {
            score = score.add(BigDecimal.valueOf(8));
        }
        if (sensitive) {
            score = score.add(BigDecimal.valueOf(5));
        }
        return score.min(BigDecimal.valueOf(100));
    }

    private String sourceLabels(boolean exec, boolean adjust, boolean sensitive) {
        List<String> labels = new ArrayList<>();
        if (exec) labels.add("EXEC");
        if (adjust) labels.add("ADJUST");
        if (sensitive) labels.add("SENSITIVE");
        return String.join(",", labels);
    }

    private String keyOf(Long planId, Long orgId, String templateCode, String itemCode) {
        return String.valueOf(planId) + "#" + String.valueOf(orgId) + "#" + templateCode + "#" + itemCode;
    }

    private static class AdjustmentSignal {
        int count = 0;
        BigDecimal total = BigDecimal.ZERO;
    }

    private Map<Long, String> deptNameMap(Set<Long> ids) {
        Map<Long, String> m = new HashMap<>();
        for (Long id : ids) {
            SysDept d = sysDeptMapper.selectById(id);
            if (d != null) {
                m.put(id, d.getDeptName());
            }
        }
        return m;
    }

    private Map<Long, String> planNameMap(Set<Long> ids) {
        Map<Long, String> m = new HashMap<>();
        if (ids.isEmpty()) {
            return m;
        }
        for (BudgetPlan p : budgetPlanMapper.selectByIds(ids)) {
            m.put(p.getId(), p.getPlanName());
        }
        return m;
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isEmpty()) return a;
        return b;
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 计算方案已过时间进度（0-100%）：按预算年度自1月至当月（含当月）占12个月比例。
     */
    private BigDecimal computeTimeRate(BudgetPlan p, int nowMonth) {
        long year = p.getBudgetYear() != null ? p.getBudgetYear() : java.time.LocalDate.now().getYear();
        long planYear = year;
        int month = nowMonth;
        int curYear = java.time.LocalDate.now().getYear();
        if (planYear < curYear) {
            return BigDecimal.valueOf(100);
        }
        if (planYear > curYear) {
            return BigDecimal.ZERO;
        }
        if (month < 1) month = 1;
        if (month > 12) month = 12;
        return BigDecimal.valueOf(month * 100 / 12);
    }

    /**
     * 累计执行金额 = Q1+Q2+Q3+Q4；若季度字段均为空则回退取 executionAmount
     */
    private BigDecimal sumExec(BudgetData d) {
        BigDecimal q1 = nvl(d.getQ1Amount());
        BigDecimal q2 = nvl(d.getQ2Amount());
        BigDecimal q3 = nvl(d.getQ3Amount());
        BigDecimal q4 = nvl(d.getQ4Amount());
        BigDecimal sum = q1.add(q2).add(q3).add(q4);
        if (sum.signum() == 0 && d.getExecutionAmount() != null) {
            return d.getExecutionAmount();
        }
        return sum;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}