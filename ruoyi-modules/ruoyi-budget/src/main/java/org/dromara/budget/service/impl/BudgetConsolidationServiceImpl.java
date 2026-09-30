package org.dromara.budget.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.*;
import org.dromara.budget.mapper.*;
import org.dromara.budget.service.IBudgetConsolidationService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.system.domain.SysDept;
import org.dromara.system.mapper.SysDeptMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 合并三表 Service实现
 *
 * 核心逻辑:
 * 1. 合并前合计 = Σ各公司已审批(APPROVED)预算数据
 * 2. 抵销调整 = -Σ已确认(CONFIRMED)内部交易金额
 * 3. 合并后金额 = 合并前合计 + 抵销调整
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetConsolidationServiceImpl implements IBudgetConsolidationService {

    private final BudgetTemplateItemMapper templateItemMapper;
    private final BudgetDataMapper budgetDataMapper;
    private final BudgetInternalTransactionMapper transactionMapper;
    private final BudgetEliminationConfigMapper configMapper;
    private final SysDeptMapper sysDeptMapper;
    private final BudgetPlanMapper planMapper;
    private final BudgetConsolidationAdjustmentMapper adjustmentMapper;

    /**
     * 校验方案是否可查看合并报表（仅执行中/已归档）
     */
    private void validatePlanViewable(Long planId) {
        BudgetPlan plan = planId == null ? null : planMapper.selectById(planId);
        if (plan == null) {
            throw new ServiceException("预算方案不存在");
        }
        if (!"PUBLISHED".equals(plan.getStatus()) && !"ARCHIVED".equals(plan.getStatus())) {
            throw new ServiceException("当前方案为草稿/关闭状态，无法查看合并报表");
        }
    }

    /**
     * 报表类型 → 预算表编号映射
     */
    private static final Map<String, String> STATEMENT_TEMPLATE_MAP = new HashMap<>();
    static {
        STATEMENT_TEMPLATE_MAP.put("IS", "B12"); // 利润预算表
        STATEMENT_TEMPLATE_MAP.put("BS", "B13"); // 资产负债表
        STATEMENT_TEMPLATE_MAP.put("CF", "B16"); // 现金流量预算表
    }

    /**
     * 报表类型 → 报表名称
     */
    private static final Map<String, String> STATEMENT_NAME_MAP = new HashMap<>();
    static {
        STATEMENT_NAME_MAP.put("IS", "合并利润预算表");
        STATEMENT_NAME_MAP.put("BS", "合并资产负债预算表");
        STATEMENT_NAME_MAP.put("CF", "合并现金流量预算表");
    }

    @Override
    public List<Map<String, Object>> getConsolidatedStatement(Long planId, String statementType, Long orgId) {
        validatePlanViewable(planId);
        String templateCode = STATEMENT_TEMPLATE_MAP.get(statementType);
        if (templateCode == null) {
            return new ArrayList<>();
        }

        // 1. 获取模板科目列表（按item_code排序）
        LambdaQueryWrapper<BudgetTemplateItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(BudgetTemplateItem::getTemplateCode, templateCode);
        itemWrapper.orderByAsc(BudgetTemplateItem::getItemCode);
        List<BudgetTemplateItem> templateItems = templateItemMapper.selectList(itemWrapper);

        if (templateItems.isEmpty()) {
            log.warn("模板 {} 没有科目项目数据", templateCode);
            return new ArrayList<>();
        }

        // 2. 获取已审批的预算数据，按item_code汇总（orgId为空=全集团，否则仅该单位）
        LambdaQueryWrapper<BudgetData> dataWrapper = new LambdaQueryWrapper<>();
        dataWrapper.eq(BudgetData::getPlanId, planId);
        dataWrapper.eq(BudgetData::getTemplateCode, templateCode);
        dataWrapper.eq(BudgetData::getStatus, "APPROVED");
        if (orgId != null) {
            dataWrapper.eq(BudgetData::getDeptId, orgId);
        }
        List<BudgetData> dataList = budgetDataMapper.selectList(dataWrapper);

        Map<String, BigDecimal> beforeMap = new HashMap<>();
        for (BudgetData data : dataList) {
            String key = data.getItemCode();
            BigDecimal amount = data.getBudgetAmount() != null ? data.getBudgetAmount() : BigDecimal.ZERO;
            beforeMap.merge(key, amount, BigDecimal::add);
        }

        // 3. 获取已确认的内部交易，按抵销项目汇总（orgId为空=全集团，否则仅该单位作为交易一方的交易）
        LambdaQueryWrapper<BudgetInternalTransaction> transWrapper = new LambdaQueryWrapper<>();
        transWrapper.eq(BudgetInternalTransaction::getPlanId, planId);
        transWrapper.eq(BudgetInternalTransaction::getTransactionType, statementType);
        transWrapper.eq(BudgetInternalTransaction::getStatus, "CONFIRMED");
        List<BudgetInternalTransaction> transactions = transactionMapper.selectList(transWrapper);
        if (orgId != null) {
            transactions = transactions.stream()
                .filter(t -> orgId.equals(t.getFromDeptId()) || orgId.equals(t.getToDeptId()))
                .collect(Collectors.toList());
        }

        // 4. 获取抵销项目配置
        LambdaQueryWrapper<BudgetEliminationConfig> configWrapper = new LambdaQueryWrapper<>();
        configWrapper.eq(BudgetEliminationConfig::getTransactionType, statementType);
        List<BudgetEliminationConfig> configs = configMapper.selectList(configWrapper);

        Map<String, String> projectKeywordMap = new HashMap<>();
        for (BudgetEliminationConfig config : configs) {
            projectKeywordMap.put(config.getProjectCode(), config.getMatchKeyword());
        }

        // 5. 按抵销项目分组汇总交易金额
        Map<String, BigDecimal> projectAmountMap = new HashMap<>();
        for (BudgetInternalTransaction t : transactions) {
            BigDecimal amount = t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO;
            projectAmountMap.merge(t.getEliminationProject(), amount, BigDecimal::add);
        }

        // 6. 将抵销金额匹配到模板科目
        Map<String, BigDecimal> adjustmentMap = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : projectAmountMap.entrySet()) {
            String projectCode = entry.getKey();
            BigDecimal totalAmount = entry.getValue();
            String keyword = projectKeywordMap.get(projectCode);

            if (StrUtil.isBlank(keyword)) {
                log.warn("抵销项目 {} 未找到配置", projectCode);
                continue;
            }

            // 在模板科目中查找匹配项
            BudgetTemplateItem matchedItem = findMatchingItem(templateItems, keyword);
            if (matchedItem != null) {
                // 抵销调整 = -金额（抵销总是减少）
                adjustmentMap.merge(matchedItem.getItemCode(), totalAmount.negate(), BigDecimal::add);
            } else {
                log.warn("抵销项目 {} 的关键词 '{}' 未匹配到任何科目", projectCode, keyword);
            }
        }

        // 7. 读取已保存的手动抵销调整，覆盖自动值
        LambdaQueryWrapper<BudgetConsolidationAdjustment> adjWrapper = new LambdaQueryWrapper<>();
        adjWrapper.eq(BudgetConsolidationAdjustment::getPlanId, planId)
            .eq(BudgetConsolidationAdjustment::getStatementType, statementType);
        List<BudgetConsolidationAdjustment> adjList = adjustmentMapper.selectList(adjWrapper);
        Map<String, BigDecimal> manualMap = new HashMap<>();
        for (BudgetConsolidationAdjustment a : adjList) {
            if (a.getAdjustAmount() != null) {
                manualMap.put(a.getItemCode(), a.getAdjustAmount());
            }
        }

        // 8. 先组装叶子明细行的原始数据，再自底向上递归汇总汇总行
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Integer> indexMap = new HashMap<>();
        for (BudgetTemplateItem item : templateItems) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemCode", item.getItemCode());
            row.put("itemName", item.getItemName());
            row.put("itemLevel", item.getItemLevel());
            row.put("isSummary", item.getIsSummary());

            BigDecimal before = beforeMap.getOrDefault(item.getItemCode(), BigDecimal.ZERO);
            BigDecimal manual = manualMap.get(item.getItemCode());
            BigDecimal adjustment;
            if (manual != null) {
                adjustment = manual;
            } else {
                adjustment = adjustmentMap.getOrDefault(item.getItemCode(), BigDecimal.ZERO);
            }
            BigDecimal after = before.add(adjustment);

            row.put("totalBefore", before);
            row.put("adjustment", adjustment);
            row.put("totalAfter", after);

            indexMap.put(item.getItemCode(), result.size());
            result.add(row);
        }

        // 自底向上（层级高的先）递归汇总：汇总行(汇总其直属子行)的金额
        List<BudgetTemplateItem> sortedDesc = new ArrayList<>(templateItems);
        sortedDesc.sort(Comparator.comparing(BudgetTemplateItem::getItemLevel, Comparator.nullsLast(Comparator.reverseOrder())));
        for (BudgetTemplateItem item : sortedDesc) {
            if (item.getItemLevel() == null || item.getItemLevel() <= 0) {
                continue;
            }
            String parentCode = item.getParentCode();
            Integer parentIdx = parentCode == null ? null : indexMap.get(parentCode);
            if (parentIdx == null) {
                continue;
            }
            BigDecimal before = (BigDecimal) result.get(parentIdx).get("totalBefore");
            BigDecimal adjustment = (BigDecimal) result.get(parentIdx).get("adjustment");
            BigDecimal after = (BigDecimal) result.get(parentIdx).get("totalAfter");
            before = before.add((BigDecimal) result.get(indexMap.get(item.getItemCode())).get("totalBefore"));
            adjustment = adjustment.add((BigDecimal) result.get(indexMap.get(item.getItemCode())).get("adjustment"));
            after = before.add(adjustment);
            result.get(parentIdx).put("totalBefore", before);
            result.get(parentIdx).put("adjustment", adjustment);
            result.get(parentIdx).put("totalAfter", after);
        }

        for (Map<String, Object> row : result) {
            row.put("totalBefore", ((BigDecimal) row.get("totalBefore")).setScale(2, RoundingMode.HALF_UP));
            row.put("adjustment", ((BigDecimal) row.get("adjustment")).setScale(2, RoundingMode.HALF_UP));
            row.put("totalAfter", ((BigDecimal) row.get("totalAfter")).setScale(2, RoundingMode.HALF_UP));
        }

        return result;
    }

    /**
     * 校验方案是否可编辑抵销调整（仅执行中 PUBLISHED）
     */
    private void validatePlanEditable(Long planId) {
        BudgetPlan plan = planId == null ? null : planMapper.selectById(planId);
        if (plan == null) {
            throw new ServiceException("预算方案不存在");
        }
        if (!"PUBLISHED".equals(plan.getStatus())) {
            throw new ServiceException("仅执行中的预算方案可调整抵销，当前方案已归档/关闭，不可编辑");
        }
    }

    /**
     * 保存手动抵销调整（按 方案+报表类型+科目 唯一键 upsert）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAdjustments(Long planId, String statementType, List<Map<String, Object>> adjustments) {
        if (planId == null || StrUtil.isBlank(statementType)) {
            throw new ServiceException("参数错误");
        }
        if (!STATEMENT_TEMPLATE_MAP.containsKey(statementType)) {
            throw new ServiceException("报表类型错误");
        }
        validatePlanEditable(planId);
        if (adjustments == null || adjustments.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : adjustments) {
            String itemCode = row.get("itemCode") == null ? null : row.get("itemCode").toString();
            if (StrUtil.isBlank(itemCode)) {
                continue;
            }
            BigDecimal amount = null;
            Object amtObj = row.get("adjustAmount");
            if (amtObj != null && StrUtil.isNotBlank(amtObj.toString())) {
                amount = new BigDecimal(amtObj.toString());
            }

            LambdaQueryWrapper<BudgetConsolidationAdjustment> qw = new LambdaQueryWrapper<>();
            qw.eq(BudgetConsolidationAdjustment::getPlanId, planId)
                .eq(BudgetConsolidationAdjustment::getStatementType, statementType)
                .eq(BudgetConsolidationAdjustment::getItemCode, itemCode);
            BudgetConsolidationAdjustment existing = adjustmentMapper.selectOne(qw);
            if (existing != null) {
                LambdaUpdateWrapper<BudgetConsolidationAdjustment> uw = new LambdaUpdateWrapper<>();
                uw.eq(BudgetConsolidationAdjustment::getId, existing.getId())
                    .set(BudgetConsolidationAdjustment::getAdjustAmount, amount);
                adjustmentMapper.update(null, uw);
            } else {
                BudgetConsolidationAdjustment na = new BudgetConsolidationAdjustment();
                na.setPlanId(planId);
                na.setStatementType(statementType);
                na.setItemCode(itemCode);
                na.setAdjustAmount(amount);
                adjustmentMapper.insert(na);
            }
        }
    }

    /**
     * 在模板科目中查找匹配关键字的科目
     * 优先匹配isSummary=1的科目（汇总行），其次匹配第一个包含关键字的科目
     */
    private BudgetTemplateItem findMatchingItem(List<BudgetTemplateItem> items, String keyword) {
        // 优先查找汇总行
        for (BudgetTemplateItem item : items) {
            if (item.getItemName() != null && item.getItemName().contains(keyword)
                    && item.getIsSummary() != null && item.getIsSummary() == 1) {
                return item;
            }
        }
        // 其次查找第一个包含关键字的明细行
        for (BudgetTemplateItem item : items) {
            if (item.getItemName() != null && item.getItemName().contains(keyword)) {
                return item;
            }
        }
        return null;
    }

    @Override
    public Map<String, Object> getConsolidationScope(Long planId, Long orgId) {
        validatePlanViewable(planId);
        Map<String, Object> result = new LinkedHashMap<>();

        Set<Long> deptIds;
        if (orgId != null) {
            // 按申报单位查看：合并范围固定为该单位
            deptIds = new HashSet<>();
            deptIds.add(orgId);
        } else {
            // 全集团：取所有已审批数据的部门列表
            LambdaQueryWrapper<BudgetData> dataWrapper = new LambdaQueryWrapper<>();
            dataWrapper.eq(BudgetData::getPlanId, planId);
            dataWrapper.eq(BudgetData::getStatus, "APPROVED");
            dataWrapper.select(BudgetData::getDeptId);
            List<BudgetData> dataList = budgetDataMapper.selectList(dataWrapper);
            deptIds = dataList.stream()
                    .map(BudgetData::getDeptId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        // 获取部门名称
        List<String> deptNames = new ArrayList<>();
        if (!deptIds.isEmpty()) {
            LambdaQueryWrapper<SysDept> deptWrapper = new LambdaQueryWrapper<>();
            deptWrapper.in(SysDept::getDeptId, deptIds);
            deptWrapper.orderByAsc(SysDept::getAncestors);
            deptWrapper.orderByAsc(SysDept::getOrderNum);
            List<SysDept> depts = sysDeptMapper.selectList(deptWrapper);
            for (SysDept dept : depts) {
                deptNames.add(dept.getDeptName());
            }
        }

        result.put("scopeDeptIds", deptIds);
        result.put("scopeDeptNames", deptNames);
        result.put("scopeDeptCount", deptNames.size());

        // 统计各报表类型的抵销数据
        Map<String, Map<String, Object>> eliminationStats = new LinkedHashMap<>();
        for (String[] typeInfo : new String[][]{{"IS", "利润表"}, {"BS", "资产负债表"}, {"CF", "现金流量表"}}) {
            String type = typeInfo[0];
            String name = typeInfo[1];

            LambdaQueryWrapper<BudgetInternalTransaction> transWrapper = new LambdaQueryWrapper<>();
            transWrapper.eq(BudgetInternalTransaction::getPlanId, planId);
            transWrapper.eq(BudgetInternalTransaction::getTransactionType, type);
            transWrapper.eq(BudgetInternalTransaction::getStatus, "CONFIRMED");
            List<BudgetInternalTransaction> transactions = transactionMapper.selectList(transWrapper);
            if (orgId != null) {
                transactions = transactions.stream()
                    .filter(t -> orgId.equals(t.getFromDeptId()) || orgId.equals(t.getToDeptId()))
                    .collect(Collectors.toList());
            }

            BigDecimal totalAmount = transactions.stream()
                    .map(t -> t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("count", transactions.size());
            stats.put("totalAmount", totalAmount.setScale(2, RoundingMode.HALF_UP));
            eliminationStats.put(name, stats);
        }
        result.put("eliminationStats", eliminationStats);

        return result;
    }

    @Override
    public List<Map<String, Object>> getEliminationSummary(Long planId, Long orgId) {
        validatePlanViewable(planId);
        List<Map<String, Object>> result = new ArrayList<>();

        // 获取已确认的内部交易
        LambdaQueryWrapper<BudgetInternalTransaction> transWrapper = new LambdaQueryWrapper<>();
        transWrapper.eq(BudgetInternalTransaction::getPlanId, planId);
        transWrapper.eq(BudgetInternalTransaction::getStatus, "CONFIRMED");
        transWrapper.orderByAsc(BudgetInternalTransaction::getTransactionType);
        transWrapper.orderByAsc(BudgetInternalTransaction::getEliminationProject);
        List<BudgetInternalTransaction> transactions = transactionMapper.selectList(transWrapper);
        if (orgId != null) {
            transactions = transactions.stream()
                .filter(t -> orgId.equals(t.getFromDeptId()) || orgId.equals(t.getToDeptId()))
                .collect(Collectors.toList());
        }

        // 获取抵销项目配置
        LambdaQueryWrapper<BudgetEliminationConfig> configWrapper = new LambdaQueryWrapper<>();
        List<BudgetEliminationConfig> configs = configMapper.selectList(configWrapper);
        Map<String, BudgetEliminationConfig> configMap = configs.stream()
                .collect(Collectors.toMap(BudgetEliminationConfig::getProjectCode, c -> c, (a, b) -> a));

        // 预加载部门名称
        Map<Long, String> deptNameMap = new HashMap<>();
        List<SysDept> allDepts = sysDeptMapper.selectList(null);
        for (SysDept dept : allDepts) {
            deptNameMap.put(dept.getDeptId(), dept.getDeptName());
        }

        // 按交易类型和抵销项目分组
        Map<String, Map<String, BigDecimal>> groupedMap = new LinkedHashMap<>();
        for (BudgetInternalTransaction t : transactions) {
            String typeKey = t.getTransactionType();
            String projectKey = t.getEliminationProject();
            String groupKey = typeKey + "|" + projectKey;

            BigDecimal amount = t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO;
            groupedMap.computeIfAbsent(groupKey, k -> {
                Map<String, BigDecimal> m = new HashMap<>();
                m.put("count", BigDecimal.ZERO);
                m.put("amount", BigDecimal.ZERO);
                return m;
            });
            groupedMap.get(groupKey).merge("count", BigDecimal.ONE, BigDecimal::add);
            groupedMap.get(groupKey).merge("amount", amount, BigDecimal::add);
        }

        // 组装结果
        for (Map.Entry<String, Map<String, BigDecimal>> entry : groupedMap.entrySet()) {
            String[] keys = entry.getKey().split("\\|");
            String type = keys[0];
            String projectCode = keys[1];
            BudgetEliminationConfig config = configMap.get(projectCode);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("transactionType", type);
            row.put("transactionTypeName", "IS".equals(type) ? "利润表抵销" : "BS".equals(type) ? "资产负债表抵销" : "现金流量表抵销");
            row.put("projectCode", projectCode);
            row.put("projectName", config != null ? config.getProjectName() : projectCode);
            row.put("count", entry.getValue().get("count").intValue());
            row.put("totalAmount", entry.getValue().get("amount").setScale(2, RoundingMode.HALF_UP));
            result.add(row);
        }

        return result;
    }
}
