package org.dromara.budget.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.*;
import org.dromara.budget.domain.vo.BudgetExecutionVo;
import org.dromara.budget.mapper.*;
import org.dromara.budget.service.IBudgetControlRuleService;
import org.dromara.budget.service.IBudgetExecutionService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.mapper.SysDeptMapper;
import org.dromara.system.domain.SysDept;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 预算执行情况 Service实现
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetExecutionServiceImpl implements IBudgetExecutionService {

    private final BudgetDataMapper budgetDataMapper;
    private final BudgetTemplateItemMapper budgetTemplateItemMapper;
    private final BudgetPlanMapper budgetPlanMapper;
    private final SysDeptMapper sysDeptMapper;
    private final IBudgetControlRuleService budgetControlRuleService;

    @Override
    public List<BudgetExecutionVo> getExecutionList(Long planId, Long orgId, String templateCode) {
        // 数据隔离：非集团账号强制限定为本单位，即使前端未传单位也不能越权查看全集团
        orgId = resolveScopeOrg(orgId);

        LambdaQueryWrapper<BudgetTemplateItem> itemWrapper = new LambdaQueryWrapper<>();
        // 按方案过滤科目，避免同时加载基础层(plan_id=0)与方案副本导致科目重复展示
        itemWrapper.eq(planId != null, BudgetTemplateItem::getPlanId, planId);
        // 排除不按"预算执行"填报口径展示的表：B01-公司基本信息、B14-预算执行情况表、B15-预算调整审批表、B17-集团本部内设部门费用
        itemWrapper.ne(BudgetTemplateItem::getTemplateCode, "B01");
        itemWrapper.notIn(StrUtil.isEmpty(templateCode), BudgetTemplateItem::getTemplateCode, Arrays.asList("B14", "B15", "B17"));
        if (StrUtil.isNotBlank(templateCode)) {
            itemWrapper.eq(BudgetTemplateItem::getTemplateCode, templateCode);
        }
        itemWrapper.orderByAsc(BudgetTemplateItem::getItemCode);
        List<BudgetTemplateItem> allItems = budgetTemplateItemMapper.selectList(itemWrapper);

        LambdaQueryWrapper<BudgetData> dataWrapper = new LambdaQueryWrapper<>();
        dataWrapper.eq(BudgetData::getPlanId, planId);
        if (orgId != null) {
            dataWrapper.eq(BudgetData::getDeptId, orgId);
        }
        if (StrUtil.isNotBlank(templateCode)) {
            dataWrapper.eq(BudgetData::getTemplateCode, templateCode);
        }
        List<BudgetData> dataList = budgetDataMapper.selectList(dataWrapper);
        Map<String, BudgetData> dataMap = dataList.stream()
            .collect(Collectors.toMap(d -> d.getTemplateCode() + "_" + d.getItemCode(), d -> d, (a, b) -> a));

        Map<Long, String> deptNameMap = new HashMap<>();
        if (orgId != null) {
            SysDept dept = sysDeptMapper.selectById(orgId);
            if (dept != null) {
                deptNameMap.put(orgId, dept.getDeptName());
            }
        }

        BudgetPlan plan = budgetPlanMapper.selectById(planId);
        String planName = plan != null ? plan.getPlanName() : "";

        List<BudgetExecutionVo> result = new ArrayList<>();
        for (BudgetTemplateItem item : allItems) {
            BudgetExecutionVo vo = new BudgetExecutionVo();
            vo.setPlanId(planId);
            vo.setPlanName(planName);
            vo.setOrgId(orgId);
            vo.setOrgName(orgId != null ? deptNameMap.get(orgId) : "");
            vo.setTemplateCode(item.getTemplateCode());
            vo.setTemplateName(item.getTemplateName());
            vo.setItemCode(item.getItemCode());
            vo.setItemName(item.getItemName());
            vo.setParentCode(item.getParentCode());
            vo.setItemLevel(item.getItemLevel());
            vo.setItemOrder(item.getItemOrder());
            vo.setIsSummary(item.getIsSummary());
            vo.setIsEditable(item.getIsEditable());

            String dataKey = item.getTemplateCode() + "_" + item.getItemCode();
            BudgetData data = dataMap.get(dataKey);
            if (data != null) {
                vo.setId(data.getId());
                // 仅已审批通过(APPROVED)的预算才计入可用预算；草稿/提交/驳回状态下预算不给金额、不给执行数
                boolean approved = "APPROVED".equals(data.getStatus());
                vo.setBudgetAmount(approved ? data.getBudgetAmount() : BigDecimal.ZERO);
                vo.setLastActual(data.getLastActual());
                vo.setStatus(data.getStatus());
                vo.setRemark(data.getRemark());
                // 季度执行金额
                vo.setQ1Amount(approved && data.getQ1Amount() != null ? data.getQ1Amount() : BigDecimal.ZERO);
                vo.setQ2Amount(approved && data.getQ2Amount() != null ? data.getQ2Amount() : BigDecimal.ZERO);
                vo.setQ3Amount(approved && data.getQ3Amount() != null ? data.getQ3Amount() : BigDecimal.ZERO);
                vo.setQ4Amount(approved && data.getQ4Amount() != null ? data.getQ4Amount() : BigDecimal.ZERO);
            } else {
                vo.setBudgetAmount(BigDecimal.ZERO);
                vo.setLastActual(BigDecimal.ZERO);
                vo.setStatus("DRAFT");
                vo.setQ1Amount(BigDecimal.ZERO);
                vo.setQ2Amount(BigDecimal.ZERO);
                vo.setQ3Amount(BigDecimal.ZERO);
                vo.setQ4Amount(BigDecimal.ZERO);
            }

            // 执行总额 = 四个季度之和
            BigDecimal totalExecution = vo.getQ1Amount()
                .add(vo.getQ2Amount())
                .add(vo.getQ3Amount())
                .add(vo.getQ4Amount());
            vo.setExecutionAmount(totalExecution);

            calculateRateAndDeviation(vo);

            // 事前控制等级：仅明细可编辑行按规则评估(用于前端红黄标与提示)
            if (Integer.valueOf(1).equals(item.getIsEditable()) && !Integer.valueOf(1).equals(item.getIsSummary())) {
                org.dromara.budget.domain.vo.BudgetControlResult ctl = budgetControlRuleService.evaluate(
                    planId, orgId, item.getTemplateCode(), vo.getBudgetAmount(), totalExecution,
                    List.of(vo.getQ1Amount(), vo.getQ2Amount(), vo.getQ3Amount(), vo.getQ4Amount()));
                if (ctl != null && !"GREEN".equals(ctl.getLevel())) {
                    vo.setControlLevel(ctl.getLevel());
                    vo.setControlMessage(ctl.getMessage());
                }
            }

            result.add(vo);
        }

        return result;
    }

    @Override
    public Map<String, Object> getExecutionSummary(Long planId, Long orgId) {
        Map<String, Object> summary = new HashMap<>();

        // 数据隔离：非集团账号强制限定为本单位
        orgId = resolveScopeOrg(orgId);

        LambdaQueryWrapper<BudgetData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BudgetData::getPlanId, planId);
        if (orgId != null) {
            wrapper.eq(BudgetData::getDeptId, orgId);
        }
        // 仅统计已审批通过的预算，草稿/提交/驳回不纳入执行口径
        wrapper.eq(BudgetData::getStatus, "APPROVED");
        List<BudgetData> dataList = budgetDataMapper.selectList(wrapper);

        BigDecimal totalBudget = BigDecimal.ZERO;
        BigDecimal totalExecution = BigDecimal.ZERO;
        int itemCount = 0;
        int executedCount = 0;

        for (BudgetData d : dataList) {
            if (d.getBudgetAmount() != null) {
                totalBudget = totalBudget.add(d.getBudgetAmount());
            }
            // 执行总额 = 四个季度之和
            BigDecimal q1 = d.getQ1Amount() != null ? d.getQ1Amount() : BigDecimal.ZERO;
            BigDecimal q2 = d.getQ2Amount() != null ? d.getQ2Amount() : BigDecimal.ZERO;
            BigDecimal q3 = d.getQ3Amount() != null ? d.getQ3Amount() : BigDecimal.ZERO;
            BigDecimal q4 = d.getQ4Amount() != null ? d.getQ4Amount() : BigDecimal.ZERO;
            BigDecimal execution = q1.add(q2).add(q3).add(q4);
            totalExecution = totalExecution.add(execution);
            if (execution.compareTo(BigDecimal.ZERO) > 0) {
                executedCount++;
            }
            itemCount++;
        }

        BigDecimal executionRate = BigDecimal.ZERO;
        if (totalBudget.compareTo(BigDecimal.ZERO) > 0) {
            executionRate = totalExecution.multiply(new BigDecimal("100"))
                .divide(totalBudget, 2, RoundingMode.HALF_UP);
        }

        BigDecimal deviation = totalBudget.subtract(totalExecution);

        summary.put("totalBudget", totalBudget);
        summary.put("totalExecution", totalExecution);
        summary.put("executionRate", executionRate);
        summary.put("deviation", deviation);
        summary.put("itemCount", itemCount);
        summary.put("executedCount", executedCount);
        summary.put("unexecutedCount", itemCount - executedCount);

        return summary;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inputExecution(Long planId, Long orgId, String templateCode, String itemCode,
                               BigDecimal q1Amount, BigDecimal q2Amount, BigDecimal q3Amount, BigDecimal q4Amount) {
        // 数据隔离：非集团账号强制限定为本单位，防止越权写入其他单位
        orgId = resolveScopeOrg(orgId);

        // 仅执行中(PUBLISHED)的方案才能录入执行数
        BudgetPlan plan = planId == null ? null : budgetPlanMapper.selectById(planId);
        if (plan == null) {
            throw new ServiceException("预算方案不存在");
        }
        if (!"PUBLISHED".equals(plan.getStatus())) {
            throw new ServiceException("仅执行中(PUBLISHED)的预算方案才能录入执行数");
        }

        // 汇总行/只读科目不允许直接录入执行数
        LambdaQueryWrapper<BudgetTemplateItem> tplWrapper = new LambdaQueryWrapper<>();
        tplWrapper.eq(BudgetTemplateItem::getTemplateCode, templateCode)
            .eq(BudgetTemplateItem::getItemCode, itemCode);
        BudgetTemplateItem tpl = budgetTemplateItemMapper.selectOne(tplWrapper);
        if (tpl != null && (Integer.valueOf(1).equals(tpl.getIsSummary()) || Integer.valueOf(0).equals(tpl.getIsEditable()))) {
            throw new ServiceException("汇总行或只读科目不允许直接录入执行数");
        }

        LambdaQueryWrapper<BudgetData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BudgetData::getPlanId, planId)
            .eq(BudgetData::getDeptId, orgId)
            .eq(BudgetData::getTemplateCode, templateCode)
            .eq(BudgetData::getItemCode, itemCode);
        BudgetData data = budgetDataMapper.selectOne(wrapper);
        if (data == null) {
            throw new ServiceException("未找到对应的预算数据，请先填报并审批通过预算");
        }
        if (!"APPROVED".equals(data.getStatus())) {
            throw new ServiceException("仅已审批通过的预算数据才能录入执行数");
        }

        // 计算执行总额
        BigDecimal totalExecution = (q1Amount != null ? q1Amount : BigDecimal.ZERO)
            .add(q2Amount != null ? q2Amount : BigDecimal.ZERO)
            .add(q3Amount != null ? q3Amount : BigDecimal.ZERO)
            .add(q4Amount != null ? q4Amount : BigDecimal.ZERO);

        // 事前控制(超预算拦截)：仅刚性控制且达控制阈值时阻止；柔性/预警放行但仍提示
        org.dromara.budget.domain.vo.BudgetControlResult ctl = budgetControlRuleService.evaluate(
            planId, orgId, templateCode, data.getBudgetAmount(), totalExecution,
            List.of(q1Amount, q2Amount, q3Amount, q4Amount));
        if (ctl.isBlocked()) {
            throw new ServiceException(ctl.getMessage());
        }

        // 使用LambdaUpdateWrapper绕过@Version乐观锁
        LambdaUpdateWrapper<BudgetData> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(BudgetData::getId, data.getId());
        updateWrapper.set(BudgetData::getQ1Amount, q1Amount != null ? q1Amount : BigDecimal.ZERO);
        updateWrapper.set(BudgetData::getQ2Amount, q2Amount != null ? q2Amount : BigDecimal.ZERO);
        updateWrapper.set(BudgetData::getQ3Amount, q3Amount != null ? q3Amount : BigDecimal.ZERO);
        updateWrapper.set(BudgetData::getQ4Amount, q4Amount != null ? q4Amount : BigDecimal.ZERO);
        updateWrapper.set(BudgetData::getExecutionAmount, totalExecution);
        budgetDataMapper.update(null, updateWrapper);
    }

    private void calculateRateAndDeviation(BudgetExecutionVo vo) {
        BigDecimal budget = vo.getBudgetAmount() != null ? vo.getBudgetAmount() : BigDecimal.ZERO;
        BigDecimal execution = vo.getExecutionAmount() != null ? vo.getExecutionAmount() : BigDecimal.ZERO;

        if (budget.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal rate = execution.multiply(new BigDecimal("100"))
                .divide(budget, 2, RoundingMode.HALF_UP);
            vo.setExecutionRate(rate);
        } else {
            vo.setExecutionRate(BigDecimal.ZERO);
        }

        vo.setDeviation(budget.subtract(execution));
    }

    /**
     * 是否为集团账号（可查看全部单位数据）：超管/租户管理员或 data_scope="1" 的角色
     */
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

    /**
     * 数据隔离：集团账号返回调用方传入的单位；非集团账号强制限定为本单位
     */
    private Long resolveScopeOrg(Long orgId) {
        if (isGlobalUser()) {
            return orgId;
        }
        return LoginHelper.getDeptId();
    }
}
