package org.dromara.budget.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.BudgetData;
import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.budget.domain.DeptBudgetModule;
import org.dromara.budget.domain.vo.BudgetSummaryVo;
import org.dromara.budget.mapper.BudgetDataMapper;
import org.dromara.budget.mapper.BudgetTemplateItemMapper;
import org.dromara.budget.mapper.DeptBudgetModuleMapper;
import org.dromara.budget.service.IBudgetSummaryService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.domain.SysDept;
import org.dromara.system.mapper.SysDeptMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 预算汇总看板 Service实现
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetSummaryServiceImpl implements IBudgetSummaryService {

    private final BudgetDataMapper budgetDataMapper;
    private final BudgetTemplateItemMapper budgetTemplateItemMapper;
    private final DeptBudgetModuleMapper deptBudgetModuleMapper;
    private final SysDeptMapper sysDeptMapper;

    /**
     * 不在"预算填报板块汇总"中展示的模板：14=执行情况表、15=调整审批表
     */
    private static final Set<String> EXCLUDED_TEMPLATES = Set.of("B14", "B15");

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
     * 读取当前用户可见的数据范围：集团账号不限；普通账号强制限定为本单位
     */
    private Long resolveScopeDeptId() {
        if (isGlobalUser()) {
            return null;
        }
        return LoginHelper.getDeptId();
    }

    @Override
    public BudgetSummaryVo.Overview getOverview(Long planId) {
        BudgetSummaryVo.Overview overview = new BudgetSummaryVo.Overview();

        // 查询该方案下所有填报数据
        LambdaQueryWrapper<BudgetData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BudgetData::getPlanId, planId);
        Long scopeDeptId = resolveScopeDeptId();
        if (scopeDeptId != null) {
            wrapper.eq(BudgetData::getDeptId, scopeDeptId);
        }
        List<BudgetData> allData = budgetDataMapper.selectList(wrapper);

        // 按部门分组
        Map<Long, List<BudgetData>> deptDataMap = allData.stream()
            .collect(Collectors.groupingBy(BudgetData::getDeptId));

        overview.setTotalDepts(deptDataMap.size());

        int submitted = 0;
        int approved = 0;
        BigDecimal totalBudget = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;

        for (Map.Entry<Long, List<BudgetData>> entry : deptDataMap.entrySet()) {
            List<BudgetData> deptData = entry.getValue();
            String deptStatus = deptData.stream()
                .map(BudgetData::getStatus)
                .filter(Objects::nonNull)
                .reduce((first, second) -> second)
                .orElse("DRAFT");

            if ("SUBMITTED".equals(deptStatus)) submitted++;
            if ("APPROVED".equals(deptStatus)) approved++;

            BigDecimal deptBudget = deptData.stream()
                .filter(d -> "APPROVED".equals(d.getStatus()))
                .map(BudgetData::getBudgetAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal deptActual = deptData.stream()
                .filter(d -> "APPROVED".equals(d.getStatus()))
                .map(BudgetData::getLastActual)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalBudget = totalBudget.add(deptBudget);
            totalActual = totalActual.add(deptActual);
        }

        overview.setSubmittedDepts(submitted);
        overview.setApprovedDepts(approved);
        overview.setFillProgress(
            overview.getTotalDepts() > 0
                ? BigDecimal.valueOf(submitted + approved)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(overview.getTotalDepts()), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO
        );
        overview.setTotalBudget(totalBudget);
        overview.setTotalActual(totalActual);
        overview.setDiffAmount(totalBudget.subtract(totalActual));
        overview.setDiffRate(
            totalActual.compareTo(BigDecimal.ZERO) != 0
                ? totalBudget.subtract(totalActual)
                .multiply(BigDecimal.valueOf(100))
                .divide(totalActual, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO
        );

        return overview;
    }

    @Override
    public List<BudgetSummaryVo.DeptSummary> getDeptSummaryList(Long planId) {
        // 查询该方案下所有填报数据
        LambdaQueryWrapper<BudgetData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BudgetData::getPlanId, planId);
        Long scopeDeptId = resolveScopeDeptId();
        if (scopeDeptId != null) {
            wrapper.eq(BudgetData::getDeptId, scopeDeptId);
        }
        List<BudgetData> allData = budgetDataMapper.selectList(wrapper);

        // 按部门分组
        Map<Long, List<BudgetData>> deptDataMap = allData.stream()
            .collect(Collectors.groupingBy(BudgetData::getDeptId));

        // 查询部门名称
        Map<Long, String> deptNameMap = new HashMap<>();
        List<SysDept> depts = sysDeptMapper.selectList(new LambdaQueryWrapper<>());
        for (SysDept dept : depts) {
            deptNameMap.put(dept.getDeptId(), dept.getDeptName());
        }

        // 查询每个模板的可编辑科目总数（按方案过滤，避免基础层+方案层重复计数）
        LambdaQueryWrapper<BudgetTemplateItem> templateWrapper = new LambdaQueryWrapper<>();
        templateWrapper.eq(BudgetTemplateItem::getIsEditable, 1);
        templateWrapper.eq(BudgetTemplateItem::getPlanId, planId);
        List<BudgetTemplateItem> templateItems = budgetTemplateItemMapper.selectList(templateWrapper);
        Map<String, Long> templateItemCount = templateItems.stream()
            .collect(Collectors.groupingBy(BudgetTemplateItem::getTemplateCode, Collectors.counting()));

        List<BudgetSummaryVo.DeptSummary> result = new ArrayList<>();
        for (Map.Entry<Long, List<BudgetData>> entry : deptDataMap.entrySet()) {
            Long deptId = entry.getKey();
            List<BudgetData> deptData = entry.getValue();

            BudgetSummaryVo.DeptSummary summary = new BudgetSummaryVo.DeptSummary();
            summary.setDeptId(deptId);
            summary.setDeptName(deptNameMap.getOrDefault(deptId, "未知部门(" + deptId + ")"));

            long filledCount = deptData.stream()
                .filter(d -> d.getBudgetAmount() != null || d.getLastActual() != null)
                .count();
            summary.setFilledItems((int) filledCount);

            Set<String> deptTemplates = deptData.stream()
                .map(BudgetData::getTemplateCode)
                .collect(Collectors.toSet());
            int totalItems = 0;
            for (String tc : deptTemplates) {
                totalItems += templateItemCount.getOrDefault(tc, 0L).intValue();
            }
            summary.setTotalItems(totalItems);

            summary.setFillProgress(
                totalItems > 0
                    ? BigDecimal.valueOf(filledCount)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalItems), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO
            );

            String deptStatus = deptData.stream()
                .map(BudgetData::getStatus)
                .filter(Objects::nonNull)
                .reduce((first, second) -> second)
                .orElse("DRAFT");
            summary.setStatus(deptStatus);

            BigDecimal deptBudget = deptData.stream()
                .filter(d -> "APPROVED".equals(d.getStatus()))
                .map(BudgetData::getBudgetAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal deptActual = deptData.stream()
                .filter(d -> "APPROVED".equals(d.getStatus()))
                .map(BudgetData::getLastActual)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            // 本年预算填报 = 该单位已填报的所有金额（不分审批状态，审批通过后仍保留原填报值）
            BigDecimal reportBudget = deptData.stream()
                .map(BudgetData::getBudgetAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            summary.setReportBudget(reportBudget);
            summary.setTotalBudget(deptBudget);
            summary.setTotalActual(deptActual);
            summary.setDiffAmount(deptBudget.subtract(deptActual));
            summary.setDiffRate(
                deptActual.compareTo(BigDecimal.ZERO) != 0
                    ? deptBudget.subtract(deptActual)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(deptActual, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO
            );

            result.add(summary);
        }

        result.sort(Comparator.comparing(BudgetSummaryVo.DeptSummary::getDeptName));
        return result;
    }

    @Override
    public List<BudgetSummaryVo.TemplateSummary> getTemplateSummaryList(Long planId) {
        // 1. 查询方案下的模板（按plan_id过滤，只展示该方案已配置的模板）
        LambdaQueryWrapper<BudgetTemplateItem> templateWrapper = new LambdaQueryWrapper<>();
        templateWrapper.eq(BudgetTemplateItem::getPlanId, planId);
        templateWrapper.orderByAsc(BudgetTemplateItem::getTemplateCode);
        List<BudgetTemplateItem> templateItems = budgetTemplateItemMapper.selectList(templateWrapper);
        // 按template_code去重，保留第一个出现的template_name
        Map<String, String> templateNameMap = new LinkedHashMap<>();
        for (BudgetTemplateItem item : templateItems) {
            templateNameMap.putIfAbsent(item.getTemplateCode(), item.getTemplateName());
        }

        // 2. 查询该方案下所有填报数据
        LambdaQueryWrapper<BudgetData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BudgetData::getPlanId, planId);
        Long scopeDeptId = resolveScopeDeptId();
        if (scopeDeptId != null) {
            wrapper.eq(BudgetData::getDeptId, scopeDeptId);
        }
        List<BudgetData> allData = budgetDataMapper.selectList(wrapper);

        // 按模板分组
        Map<String, List<BudgetData>> templateDataMap = allData.stream()
            .collect(Collectors.groupingBy(BudgetData::getTemplateCode));

        // 3. 普通账号仅展示本单位已配置的板块
        Set<String> allowedCodes = null;
        if (scopeDeptId != null) {
            allowedCodes = deptBudgetModuleMapper.selectList(
                    new LambdaQueryWrapper<DeptBudgetModule>().eq(DeptBudgetModule::getDeptId, scopeDeptId))
                .stream().map(DeptBudgetModule::getTemplateCode).collect(Collectors.toSet());
        }

        // 4. 遍历模板（排除执行/调整表；本单位仅展示其配置的板块）
        List<BudgetSummaryVo.TemplateSummary> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : templateNameMap.entrySet()) {
            String templateCode = entry.getKey();
            if (EXCLUDED_TEMPLATES.contains(templateCode)) continue;
            if (allowedCodes != null && !allowedCodes.contains(templateCode)) continue;
            List<BudgetData> templateData = templateDataMap.getOrDefault(templateCode, Collections.emptyList());

            BudgetSummaryVo.TemplateSummary summary = new BudgetSummaryVo.TemplateSummary();
            summary.setTemplateCode(templateCode);
            summary.setTemplateName(entry.getValue());

            Set<Long> deptSet = templateData.stream()
                .map(BudgetData::getDeptId)
                .collect(Collectors.toSet());
            summary.setDeptCount(deptSet.size());

            long filledDepts = templateData.stream()
                .filter(d -> d.getBudgetAmount() != null || d.getLastActual() != null)
                .map(BudgetData::getDeptId)
                .distinct()
                .count();
            summary.setFilledDeptCount((int) filledDepts);

            BigDecimal templateBudget = templateData.stream()
                .filter(d -> "APPROVED".equals(d.getStatus()))
                .map(BudgetData::getBudgetAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal templateActual = templateData.stream()
                .filter(d -> "APPROVED".equals(d.getStatus()))
                .map(BudgetData::getLastActual)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            // 本年预算填报 = 该表所有单位的填报金额（不分审批状态）
            BigDecimal reportBudget = templateData.stream()
                .map(BudgetData::getBudgetAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            summary.setReportBudget(reportBudget);
            summary.setTotalBudget(templateBudget);
            summary.setTotalActual(templateActual);

            result.add(summary);
        }

        result.sort(Comparator.comparing(BudgetSummaryVo.TemplateSummary::getTemplateCode));
        return result;
    }
}
