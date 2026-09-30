package org.dromara.budget.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.*;
import org.dromara.budget.mapper.*;
import org.dromara.budget.service.IBudgetEliminationService;
import org.dromara.system.domain.SysDept;
import org.dromara.system.mapper.SysDeptMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 抵销分录 Service实现
 *
 * 每笔已确认内部交易 → 一条抵销分录：
 *  - 从 budget_internal_transaction 取交易（双方单位/金额/抵销项目）
 *  - 从 budget_elimination_config 取抵销项目名称与关键词
 *  - 在对应预算表的模板科目里匹配关键词得到"关联科目"
 *  - 抵销金额 = 交易金额取负（抵销恒为冲减）
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetEliminationServiceImpl implements IBudgetEliminationService {

    private final BudgetInternalTransactionMapper transactionMapper;
    private final BudgetEliminationConfigMapper configMapper;
    private final BudgetTemplateItemMapper templateItemMapper;
    private final BudgetPlanMapper planMapper;
    private final SysDeptMapper sysDeptMapper;

    /** 交易类型 → 名称 */
    private static final Map<String, String> TYPE_NAME_MAP = new HashMap<>();
    static {
        TYPE_NAME_MAP.put("IS", "利润表抵销");
        TYPE_NAME_MAP.put("BS", "资产负债表抵销");
        TYPE_NAME_MAP.put("CF", "现金流量表抵销");
    }

    @Override
    public List<Map<String, Object>> getEliminationEntries(Long planId, String transactionType, String projectCode) {
        if (planId == null) {
            return new ArrayList<>();
        }

        // 1. 查询已确认的内部交易
        LambdaQueryWrapper<BudgetInternalTransaction> txWrapper = new LambdaQueryWrapper<>();
        txWrapper.eq(BudgetInternalTransaction::getPlanId, planId);
        txWrapper.eq(BudgetInternalTransaction::getStatus, "CONFIRMED");
        txWrapper.eq(StrUtil.isNotBlank(transactionType),
            BudgetInternalTransaction::getTransactionType, transactionType);
        txWrapper.eq(StrUtil.isNotBlank(projectCode),
            BudgetInternalTransaction::getEliminationProject, projectCode);
        txWrapper.orderByAsc(BudgetInternalTransaction::getTransactionType);
        txWrapper.orderByAsc(BudgetInternalTransaction::getEliminationProject);
        List<BudgetInternalTransaction> transactions = transactionMapper.selectList(txWrapper);

        if (transactions.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 抵销项目配置
        List<BudgetEliminationConfig> configs = configMapper.selectList(null);
        Map<String, BudgetEliminationConfig> configMap = configs.stream()
            .collect(Collectors.toMap(BudgetEliminationConfig::getProjectCode, c -> c, (a, b) -> a));

        // 3. 部门名称
        Map<Long, String> deptNameMap = new HashMap<>();
        List<SysDept> depts = sysDeptMapper.selectList(null);
        for (SysDept d : depts) {
            deptNameMap.put(d.getDeptId(), d.getDeptName());
        }

        // 4. 方案名称
        BudgetPlan plan = planMapper.selectById(planId);
        String planName = plan != null ? plan.getPlanName() : "";

        List<Map<String, Object>> result = new ArrayList<>();
        BigDecimal totalElimination = BigDecimal.ZERO;
        for (BudgetInternalTransaction t : transactions) {
            BudgetEliminationConfig config = configMap.get(t.getEliminationProject());
            String projectName = config != null ? config.getProjectName() : t.getEliminationProject();
            String keyword = config != null ? config.getMatchKeyword() : "";
            String templateCode = config != null ? config.getTemplateCode() : "";
            BudgetTemplateItem matchedItem = findMatchingItem(templateCode, keyword);

            BigDecimal amount = t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO;
            BigDecimal eliminationAmount = amount.negate();
            totalElimination = totalElimination.add(eliminationAmount);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", t.getId());
            row.put("planId", planId);
            row.put("planName", planName);
            row.put("transactionType", t.getTransactionType());
            row.put("transactionTypeName", TYPE_NAME_MAP.getOrDefault(t.getTransactionType(), "抵销"));
            row.put("fromDeptId", t.getFromDeptId());
            row.put("fromDeptName", deptNameMap.getOrDefault(t.getFromDeptId(), String.valueOf(t.getFromDeptId())));
            row.put("toDeptId", t.getToDeptId());
            row.put("toDeptName", deptNameMap.getOrDefault(t.getToDeptId(), String.valueOf(t.getToDeptId())));
            row.put("eliminationProject", t.getEliminationProject());
            row.put("projectName", projectName);
            row.put("templateCode", templateCode);
            row.put("templateName", matchedItem != null ? matchedItem.getTemplateName() : "");
            row.put("itemCode", matchedItem != null ? matchedItem.getItemCode() : "");
            row.put("itemName", matchedItem != null ? matchedItem.getItemName() : "");
            row.put("amount", amount.setScale(2, BigDecimal.ROUND_HALF_UP));
            row.put("direction", ("IS".equals(t.getTransactionType()) || "CF".equals(t.getTransactionType())) ? "贷方抵减" : "借方抵减");
            row.put("eliminationAmount", eliminationAmount.setScale(2, BigDecimal.ROUND_HALF_UP));
            row.put("transactionDate", t.getTransactionDate());
            row.put("remark", t.getRemark());
            result.add(row);
        }

        // 5. 汇总行放在最后
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("isSummary", true);
        summary.put("transactionTypeName", "抵销合计");
        summary.put("fromDeptName", "");
        summary.put("toDeptName", "");
        summary.put("projectName", "");
        summary.put("itemCode", "");
        summary.put("itemName", "");
        summary.put("amount", "—");
        summary.put("direction", "");
        summary.put("eliminationAmount", totalElimination.setScale(2, BigDecimal.ROUND_HALF_UP));
        result.add(summary);

        return result;
    }

    /**
     * 在指定预算表的模板科目中查找匹配关键字的科目
     */
    private BudgetTemplateItem findMatchingItem(String templateCode, String keyword) {
        if (StrUtil.isBlank(templateCode) || StrUtil.isBlank(keyword)) {
            return null;
        }
        LambdaQueryWrapper<BudgetTemplateItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BudgetTemplateItem::getTemplateCode, templateCode);
        wrapper.orderByAsc(BudgetTemplateItem::getItemCode);
        List<BudgetTemplateItem> items = templateItemMapper.selectList(wrapper);
        // 优先匹配汇总行(isSummary=1)，其次匹配第一个包含关键字的明细行
        for (BudgetTemplateItem item : items) {
            if (item.getItemName() != null && item.getItemName().contains(keyword)
                && item.getIsSummary() != null && item.getIsSummary() == 1) {
                return item;
            }
        }
        for (BudgetTemplateItem item : items) {
            if (item.getItemName() != null && item.getItemName().contains(keyword)) {
                return item;
            }
        }
        return null;
    }
}