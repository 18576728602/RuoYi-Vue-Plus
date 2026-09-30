package org.dromara.budget.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.*;
import org.dromara.budget.mapper.*;
import org.dromara.budget.service.IBudgetDashboardService;
import org.dromara.budget.service.IBudgetControlRuleService;
import org.dromara.budget.domain.BudgetControlRule;
import org.dromara.budget.util.BudgetUnitNameUtil;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.mapper.SysDeptMapper;
import org.dromara.system.domain.SysDept;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 预算工作台仪表盘 Service实现
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetDashboardServiceImpl implements IBudgetDashboardService {

    private final BudgetPlanMapper budgetPlanMapper;
    private final BudgetDataMapper budgetDataMapper;
    private final BudgetAdjustmentMapper budgetAdjustmentMapper;
    private final BudgetTemplateItemMapper budgetTemplateItemMapper;
    private final BudgetOperationLogMapper budgetOperationLogMapper;
    private final SysDeptMapper sysDeptMapper;
    private final IBudgetControlRuleService budgetControlRuleService;
    private final org.dromara.budget.mapper.BudgetApprovalRecordMapper budgetApprovalRecordMapper;

    /**
     * 是否为集团账号（可查看全部单位数据）
     * 超级管理员或租户管理员（集团管理员）→ 看全部；其余用户 → 只看本单位
     */
    private boolean isGlobalUser() {
        if (LoginHelper.isSuperAdmin() || LoginHelper.isTenantAdmin()) {
            return true;
        }
        // 集团管理员等 data_scope="1"（全部数据权限）的角色也视为全局，可看集团本部+全部子公司
        try {
            java.util.List<org.dromara.common.core.domain.dto.RoleDTO> roles = LoginHelper.getLoginUser().getRoles();
            if (roles != null) {
                return roles.stream().anyMatch(r -> "1".equals(r.getDataScope()));
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * 解析当前用户可见的数据范围：
     * 集团账号遵循前端传入的 orgId（可自由筛选所有单位）；
     * 普通账号强制限定为本单位，忽略前端传入的其他单位筛选。
     */
    private Long resolveScopeOrg(Long requestedOrgId) {
        if (isGlobalUser()) {
            return requestedOrgId;
        }
        return LoginHelper.getDeptId();
    }

    @Override
    public Map<String, Object> getStats(Long planId, Long orgId) {
        Map<String, Object> stats = new HashMap<>();

        // 1. 预算方案数（已发布 + 已归档）；指定方案则为1
        LambdaQueryWrapper<BudgetPlan> planWrapper = new LambdaQueryWrapper<>();
        if (planId == null) {
            planWrapper.in(BudgetPlan::getStatus, "PUBLISHED", "ARCHIVED");
        } else {
            planWrapper.eq(BudgetPlan::getId, planId);
        }
        Long planCount = budgetPlanMapper.selectCount(planWrapper);
        stats.put("planCount", planCount);

        // 2. 填报数据统计（按方案/单位过滤，普通账号默认限定本单位）
        Long scopeOrg = resolveScopeOrg(orgId);
        LambdaQueryWrapper<BudgetData> dataWrapper = new LambdaQueryWrapper<>();
        if (planId != null) {
            dataWrapper.eq(BudgetData::getPlanId, planId);
        }
        if (scopeOrg != null) {
            dataWrapper.eq(BudgetData::getDeptId, scopeOrg);
        }
        List<BudgetData> allData = budgetDataMapper.selectList(dataWrapper);

        Long totalItems = (long) allData.size();
        Long submittedCount = allData.stream().filter(d -> "SUBMITTED".equals(d.getStatus())).count();
        Long approvedCount = allData.stream().filter(d -> "APPROVED".equals(d.getStatus())).count();
        Long draftCount = allData.stream().filter(d -> "DRAFT".equals(d.getStatus())).count();

        stats.put("totalItems", totalItems);
        stats.put("submittedCount", submittedCount);
        stats.put("approvedCount", approvedCount);
        stats.put("draftCount", draftCount);

        // 3. 预算总金额（只统计已审批 APPROVED，草稿未生效不计入）
        BigDecimal totalBudget = allData.stream()
            .filter(d -> "APPROVED".equals(d.getStatus()))
            .map(BudgetData::getBudgetAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("totalBudget", totalBudget);

        // 4. 执行总金额（执行数仅存在于已审批数据，同样只取 APPROVED）
        BigDecimal totalExecution = allData.stream()
            .filter(d -> "APPROVED".equals(d.getStatus()))
            .map(BudgetData::getExecutionAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("totalExecution", totalExecution);

        // 5. 执行率
        BigDecimal executionRate = BigDecimal.ZERO;
        if (totalBudget.compareTo(BigDecimal.ZERO) > 0) {
            executionRate = totalExecution.multiply(new BigDecimal("100"))
                .divide(totalBudget, 2, java.math.RoundingMode.HALF_UP);
        }
        stats.put("executionRate", executionRate);

        // 6. 待审批调整数
        LambdaQueryWrapper<BudgetAdjustment> adjWrapper = new LambdaQueryWrapper<>();
        adjWrapper.eq(BudgetAdjustment::getStatus, "PENDING");
        Long pendingAdjustment = budgetAdjustmentMapper.selectCount(adjWrapper);
        stats.put("pendingAdjustment", pendingAdjustment);

        // 7. 单位数
        Set<Long> orgIds = allData.stream()
            .map(BudgetData::getDeptId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        stats.put("orgCount", orgIds.size());

        // 8. 预算执行预警计数（口径与"预算执行预警"一致：排除01模板与汇总行）
        LambdaQueryWrapper<BudgetTemplateItem> warnItemWrapper = new LambdaQueryWrapper<>();
        warnItemWrapper.ne(BudgetTemplateItem::getTemplateCode, "B01");
        List<BudgetTemplateItem> warnItems = budgetTemplateItemMapper.selectList(warnItemWrapper);
        Map<String, Boolean> isSummaryByCode = new HashMap<>();
        for (BudgetTemplateItem it : warnItems) {
            isSummaryByCode.put(it.getItemCode(), it.getIsSummary() != null && it.getIsSummary() == 1);
        }
        long warnRed = 0;
        long warnYellow = 0;
        for (BudgetData d : allData) {
            if (d.getItemCode() == null || d.getTemplateCode() == null) continue;
            if ("B01".equals(d.getTemplateCode())) continue;
            Boolean isSummary = isSummaryByCode.get(d.getItemCode());
            if (isSummary != null && isSummary) continue;
            BigDecimal budget = d.getBudgetAmount();
            BigDecimal exec = d.getExecutionAmount();
            if (budget == null || budget.signum() <= 0) continue;
            if (exec == null || exec.signum() <= 0) continue;
            if (exec.compareTo(budget) > 0) {
                warnRed++;
            } else {
                BigDecimal rate = exec.multiply(new BigDecimal("100"))
                    .divide(budget, 2, java.math.RoundingMode.HALF_UP);
                if (rate.compareTo(new BigDecimal("80")) >= 0) {
                    warnYellow++;
                }
            }
        }
        stats.put("warnRed", warnRed);
        stats.put("warnYellow", warnYellow);
        stats.put("warnTotal", warnRed + warnYellow);

        return stats;
    }

    @Override
    public List<Map<String, Object>> getFillProgress(Long planId, Long orgId) {
        // 查所有填报数据（按单位过滤，方案不强制过滤 → 以方案+单位维度展示）
        Long scopeOrg = resolveScopeOrg(orgId);
        LambdaQueryWrapper<BudgetData> dataWrapper = new LambdaQueryWrapper<>();
        if (scopeOrg != null) {
            dataWrapper.eq(BudgetData::getDeptId, scopeOrg);
        }
        List<BudgetData> allData = budgetDataMapper.selectList(dataWrapper);

        // 查询汇总行科目编码，统计时排除
        Set<String> summaryCodes = new HashSet<>();
        if (!allData.isEmpty()) {
            Set<Long> planIds = new HashSet<>();
            Set<String> itemCodes = new HashSet<>();
            for (BudgetData d : allData) {
                if (d.getPlanId() != null) planIds.add(d.getPlanId());
                if (d.getItemCode() != null) itemCodes.add(d.getItemCode());
            }
            if (!planIds.isEmpty() && !itemCodes.isEmpty()) {
                List<BudgetTemplateItem> summaryItems = budgetTemplateItemMapper.selectList(
                    new LambdaQueryWrapper<BudgetTemplateItem>()
                        .in(BudgetTemplateItem::getPlanId, planIds)
                        .in(BudgetTemplateItem::getItemCode, itemCodes)
                        .eq(BudgetTemplateItem::getIsSummary, 1));
                for (BudgetTemplateItem it : summaryItems) {
                    summaryCodes.add(it.getItemCode());
                }
            }
        }
        // 过滤掉汇总行
        allData = allData.stream()
            .filter(d -> !summaryCodes.contains(d.getItemCode()))
            .collect(Collectors.toList());

        // 预加载部门（用于单位显示名格式化）
        Map<Long, SysDept> deptMap = new HashMap<>();
        List<SysDept> allDepts = sysDeptMapper.selectList(null);
        for (SysDept sd : allDepts) {
            deptMap.put(sd.getDeptId(), sd);
        }

        // 预加载方案名称
        Map<Long, String> planNameMap = new HashMap<>();
        try {
            List<BudgetPlan> plans = budgetPlanMapper.selectList(null);
            for (BudgetPlan p : plans) {
                planNameMap.put(p.getId(), p.getPlanName());
            }
        } catch (Exception e) {
            log.warn("加载方案名称失败", e);
        }

        // 按方案+单位分组
        Map<String, List<BudgetData>> groupMap = new LinkedHashMap<>();
        for (BudgetData d : allData) {
            if (d.getDeptId() == null) continue;
            Long dPlanId = d.getPlanId();
            String key = (dPlanId == null ? "-1" : dPlanId) + "_" + d.getDeptId();
            groupMap.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<BudgetData>> entry : groupMap.entrySet()) {
            Long dPlanId = null;
            try { dPlanId = Long.parseLong(entry.getKey().split("_")[0]); } catch (Exception ignore) {}
            if (dPlanId != null && dPlanId == -1L) dPlanId = null;
            Long deptId = null;
            try { deptId = Long.parseLong(entry.getKey().split("_")[1]); } catch (Exception ignore) {}
            if (deptId == null) continue;
            List<BudgetData> dataList = entry.getValue();

            // 指定方案时，仅统计该方案的进度，其余方案的行不再返回
            if (planId != null && dPlanId != null && !planId.equals(dPlanId)) continue;

            SysDept dept = deptMap.get(deptId);
            SysDept deptParent = dept != null && dept.getParentId() != null ? deptMap.get(dept.getParentId()) : null;
            String deptName = dept != null
                ? BudgetUnitNameUtil.format(dept.getDeptName(), deptParent != null ? deptParent.getDeptName() : null)
                : "未知单位";

            int total = dataList.size();
            long submitted = dataList.stream().filter(d -> "SUBMITTED".equals(d.getStatus())).count();
            long approved = dataList.stream().filter(d -> "APPROVED".equals(d.getStatus())).count();
            long draft = dataList.stream().filter(d -> "DRAFT".equals(d.getStatus())).count();

            // 预算金额包含已提交和已审批的数据
            BigDecimal budget = dataList.stream()
                .filter(d -> "SUBMITTED".equals(d.getStatus()) || "APPROVED".equals(d.getStatus()))
                .map(BudgetData::getBudgetAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal execution = dataList.stream()
                .filter(d -> "APPROVED".equals(d.getStatus()))
                .map(BudgetData::getExecutionAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            int progress = total > 0 ? (int) ((submitted + approved) * 100 / total) : 0;

            Map<String, Object> item = new HashMap<>();
            item.put("planId", dPlanId);
            item.put("planName", dPlanId != null ? planNameMap.getOrDefault(dPlanId, "-") : "未关联方案");
            item.put("deptId", deptId);
            item.put("deptName", deptName);
            item.put("total", total);
            item.put("submitted", submitted);
            item.put("approved", approved);
            item.put("draft", draft);
            item.put("budget", budget);
            item.put("execution", execution);
            item.put("progress", progress);

            result.add(item);
        }

        // 按方案→进度排序
        result.sort((a, b) -> {
            int pn = String.valueOf(a.get("planName")).compareTo(String.valueOf(b.get("planName")));
            if (pn != 0) return pn;
            return Integer.compare((int) b.get("progress"), (int) a.get("progress"));
        });

        return result;
    }

    @Override
    public List<Map<String, Object>> getTodoList() {
        List<Map<String, Object>> todoList = new ArrayList<>();

        Long scopeOrg = resolveScopeOrg(null);
        boolean scoped = scopeOrg != null;

        // 1. 待审批的填报数据
        LambdaQueryWrapper<BudgetData> dataWrapper = new LambdaQueryWrapper<>();
        dataWrapper.eq(BudgetData::getStatus, "SUBMITTED");
        if (scoped) {
            dataWrapper.eq(BudgetData::getDeptId, scopeOrg);
        }
        List<BudgetData> submittedData = budgetDataMapper.selectList(dataWrapper);

        Map<Long, List<BudgetData>> orgMap = submittedData.stream()
            .filter(d -> d.getDeptId() != null)
            .collect(Collectors.groupingBy(BudgetData::getDeptId));

        for (Map.Entry<Long, List<BudgetData>> entry : orgMap.entrySet()) {
            SysDept dept = sysDeptMapper.selectById(entry.getKey());
            String deptName = dept != null ? dept.getDeptName() : "未知单位";

            Map<String, Object> todo = new HashMap<>();
            todo.put("type", "fill_approval");
            todo.put("title", deptName + " 预算填报待审批");
            todo.put("count", entry.getValue().size());
            todo.put("deptId", entry.getKey());
            todoList.add(todo);
        }

        // 2. 待审批的调整申请
        LambdaQueryWrapper<BudgetAdjustment> adjWrapper = new LambdaQueryWrapper<>();
        adjWrapper.eq(BudgetAdjustment::getStatus, "PENDING");
        if (scoped) {
            adjWrapper.eq(BudgetAdjustment::getOrgId, scopeOrg);
        }
        List<BudgetAdjustment> pendingAdj = budgetAdjustmentMapper.selectList(adjWrapper);

        if (!pendingAdj.isEmpty()) {
            Map<String, Object> todo = new HashMap<>();
            todo.put("type", "adjustment_approval");
            todo.put("title", "预算调整申请待审批");
            todo.put("count", pendingAdj.size());
            todo.put("deptId", null);
            todoList.add(todo);
        }

        // 3. 超预算警示（执行数触碰控制阈值）
        long overBudgetCount = countOverBudgetItems(scopeOrg);
        if (overBudgetCount > 0) {
            Map<String, Object> todo = new HashMap<>();
            todo.put("type", "over_budget");
            todo.put("title", "存在超预算科目需要关注");
            todo.put("count", overBudgetCount);
            todo.put("deptId", scopeOrg);
            todoList.add(todo);
        }

        return todoList;
    }

    /**
     * 统计触碰预算控制阈值的科目数（用于工作台警示待办）。
     * 口径：取已审批(APPROVED)的执行数据，预算>0 且执行率 >= 控制阈值(未配置规则时默认100%)。
     *
     * @param orgId 单位过滤，null 表示全部（调用方已按数据权限限缩）
     */
    private long countOverBudgetItems(Long orgId) {
        LambdaQueryWrapper<BudgetData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BudgetData::getStatus, "APPROVED");
        if (orgId != null) {
            wrapper.eq(BudgetData::getDeptId, orgId);
        }
        List<BudgetData> approved = budgetDataMapper.selectList(wrapper);
        long count = 0;
        for (BudgetData d : approved) {
            BigDecimal budget = d.getBudgetAmount();
            if (budget == null || budget.signum() <= 0) {
                continue;
            }
            BigDecimal exec = d.getExecutionAmount();
            if (exec == null || exec.signum() <= 0) {
                // 兼容季度字段存储
                exec = nvl(d.getQ1Amount()).add(nvl(d.getQ2Amount()))
                    .add(nvl(d.getQ3Amount())).add(nvl(d.getQ4Amount()));
            }
            if (exec.signum() <= 0) {
                continue;
            }
            // 命中规则取控制阈值，否则默认100%
            BudgetControlRule rule = budgetControlRuleService.matchRule(d.getPlanId(), d.getDeptId(), d.getTemplateCode());
            BigDecimal block = rule != null && rule.getBlockPercent() != null ? rule.getBlockPercent() : new BigDecimal("100");
            BigDecimal rate = exec.multiply(new BigDecimal("100")).divide(budget, 6, java.math.RoundingMode.HALF_UP);
            if (rate.compareTo(block) >= 0) {
                count++;
            }
        }
        return count;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    @Override
    public List<Map<String, Object>> getRecentActivities(Long planId, Long orgId) {
        List<Map<String, Object>> activities = new ArrayList<>();
        Long scopeOrg = resolveScopeOrg(orgId);

        // 最新动态以预算操作日志为准：记录每一步动作的真实时刻，非当前状态快照
        LambdaQueryWrapper<BudgetOperationLog> wrapper = new LambdaQueryWrapper<>();
        if (planId != null) {
            wrapper.eq(BudgetOperationLog::getPlanId, planId);
        }
        if (scopeOrg != null) {
            wrapper.eq(BudgetOperationLog::getDeptId, scopeOrg);
        }
        wrapper.isNotNull(BudgetOperationLog::getCreateTime)
            .orderByDesc(BudgetOperationLog::getCreateTime)
            .last("LIMIT 15");
        List<BudgetOperationLog> logs = budgetOperationLogMapper.selectList(wrapper);

        for (BudgetOperationLog log : logs) {
            Map<String, Object> activity = new HashMap<>();
            SysDept dept = log.getDeptId() != null ? sysDeptMapper.selectById(log.getDeptId()) : null;
            String deptName = dept != null ? dept.getDeptName() : "未知单位";

            activity.put("type", "ADJUSTMENT".equals(log.getTargetType()) ? "adjustment" : "fill");
            activity.put("title", deptName + " " + log.getActionLabel());
            activity.put("planId", log.getPlanId());
            activity.put("deptId", log.getDeptId());
            activity.put("planName", log.getPlanName());
            activity.put("templateCode", log.getTemplateCode());
            activity.put("templateName", log.getTemplateName());
            activity.put("operatorName", log.getOperatorName());
            activity.put("remark", log.getRemark());
            activity.put("time", log.getCreateTime());

            // 查询该次提交对应的审批记录轮次（submitRound = versionNo）
            Integer submitRound = null;
            if (log.getPlanId() != null && log.getDeptId() != null) {
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<org.dromara.budget.domain.BudgetApprovalRecord> rw =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                rw.eq(org.dromara.budget.domain.BudgetApprovalRecord::getPlanId, log.getPlanId())
                    .eq(org.dromara.budget.domain.BudgetApprovalRecord::getDeptId, log.getDeptId());
                if (log.getTemplateCode() != null) {
                    rw.eq(org.dromara.budget.domain.BudgetApprovalRecord::getTemplateCode, log.getTemplateCode());
                }
                // SUBMIT操作找提交时间最接近的记录（提交时间 <= 操作时间）
                if ("SUBMIT".equals(log.getActionType())) {
                    rw.le(org.dromara.budget.domain.BudgetApprovalRecord::getSubmitTime, log.getCreateTime())
                        .orderByDesc(org.dromara.budget.domain.BudgetApprovalRecord::getSubmitTime)
                        .last("LIMIT 1");
                } else {
                    // APPROVE/REJECT 找最近的记录
                    rw.orderByDesc(org.dromara.budget.domain.BudgetApprovalRecord::getSubmitTime)
                        .last("LIMIT 1");
                }
                org.dromara.budget.domain.BudgetApprovalRecord record = budgetApprovalRecordMapper.selectOne(rw);
                if (record != null) {
                    submitRound = record.getSubmitRound();
                }
            }
            activity.put("submitRound", submitRound);
            activities.add(activity);
        }

        return activities;
    }

    @Override
    public Map<String, Object> getQuarterTrend(Long planId, Long orgId) {
        Long scopeOrg = resolveScopeOrg(orgId);
        LambdaQueryWrapper<BudgetData> dataWrapper = new LambdaQueryWrapper<>();
        if (planId != null) {
            dataWrapper.eq(BudgetData::getPlanId, planId);
        }
        if (scopeOrg != null) {
            dataWrapper.eq(BudgetData::getDeptId, scopeOrg);
        }
        List<BudgetData> allData = budgetDataMapper.selectList(dataWrapper);

        BigDecimal q1 = new BigDecimal("0"), q2 = new BigDecimal("0"), q3 = new BigDecimal("0"), q4 = new BigDecimal("0");
        BigDecimal budgetTotal = new BigDecimal("0");
        for (BudgetData d : allData) {
            if (d.getQ1Amount() != null) q1 = q1.add(d.getQ1Amount());
            if (d.getQ2Amount() != null) q2 = q2.add(d.getQ2Amount());
            if (d.getQ3Amount() != null) q3 = q3.add(d.getQ3Amount());
            if (d.getQ4Amount() != null) q4 = q4.add(d.getQ4Amount());
            if (d.getBudgetAmount() != null) budgetTotal = budgetTotal.add(d.getBudgetAmount());
        }
        // 兼容仅存储执行总额的旧数据
        BigDecimal legacyExec = allData.stream()
            .map(BudgetData::getExecutionAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (q1.add(q2).add(q3).add(q4).compareTo(BigDecimal.ZERO) == 0) {
            q1 = legacyExec; // 无法区分季度时全部归到Q1展示
        }
        BigDecimal totalExec = q1.add(q2).add(q3).add(q4);

        List<Map<String, Object>> quarters = new ArrayList<>();
        java.math.RoundingMode rm = java.math.RoundingMode.HALF_UP;
        BigDecimal[] qArr = {q1, q2, q3, q4};
        for (int i = 0; i < 4; i++) {
            Map<String, Object> q = new HashMap<>();
            q.put("label", "Q" + (i + 1));
            q.put("exec", qArr[i]);
            BigDecimal rate = BigDecimal.ZERO;
            if (budgetTotal.compareTo(BigDecimal.ZERO) > 0) {
                rate = qArr[i].multiply(new BigDecimal("100")).divide(budgetTotal, 2, rm);
            }
            q.put("rate", rate);
            quarters.add(q);
        }
        // 整体执行率
        BigDecimal overallRate = BigDecimal.ZERO;
        if (budgetTotal.compareTo(BigDecimal.ZERO) > 0) {
            overallRate = totalExec.multiply(new BigDecimal("100")).divide(budgetTotal, 2, rm);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("budgetTotal", budgetTotal);
        result.put("totalExec", totalExec);
        result.put("q1", q1);
        result.put("q2", q2);
        result.put("q3", q3);
        result.put("q4", q4);
        result.put("overallRate", overallRate);
        result.put("quarters", quarters);
        return result;
    }

    @Override
    public List<Map<String, Object>> getUnitOptions() {
        // 单位口径与预算主表一致：集团下面所有子公司和本部（排除国资委和内设部门）
        List<SysDept> allDepts = sysDeptMapper.selectList(null);
        List<SysDept> reportDepts = allDepts.stream()
            .filter(d -> {
                if (d.getParentId() == null || d.getParentId() == 0) return false;
                SysDept parent = allDepts.stream()
                    .filter(p -> p.getDeptId().equals(d.getParentId()))
                    .findFirst().orElse(null);
                if (parent == null) return false;
                SysDept grandParent = allDepts.stream()
                    .filter(g -> g.getDeptId().equals(parent.getParentId()))
                    .findFirst().orElse(null);
                if (grandParent == null) return false;
                return grandParent.getParentId() != null && grandParent.getParentId() == 0;
            })
            .sorted(Comparator.comparing(SysDept::getOrderNum))
            .collect(Collectors.toList());

        // 退而求其次：取所有非顶级部门，排除编码含D的内设部门
        if (reportDepts.isEmpty()) {
            reportDepts = allDepts.stream()
                .filter(d -> d.getParentId() != null && d.getParentId() != 0)
                .filter(d -> {
                    String category = d.getDeptCategory();
                    return category == null || !category.contains("D");
                })
                .sorted(Comparator.comparing(SysDept::getOrderNum))
                .collect(Collectors.toList());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        // 普通账号仅返回本单位，集团账号返回全部填报单位
        Long scopeOrg = isGlobalUser() ? null : LoginHelper.getDeptId();
        for (SysDept dept : reportDepts) {
            if (scopeOrg != null && !scopeOrg.equals(dept.getDeptId())) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("deptId", dept.getDeptId());
            item.put("deptName", dept.getDeptName());
            result.add(item);
        }
        return result;
    }
}
