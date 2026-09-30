package org.dromara.budget.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.BudgetData;
import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.budget.domain.BudgetValidateRule;
import org.dromara.budget.domain.bo.BudgetValidateQuery;
import org.dromara.budget.domain.vo.BudgetValidateResult;
import org.dromara.budget.mapper.BudgetDataMapper;
import org.dromara.budget.mapper.BudgetTemplateItemMapper;
import org.dromara.budget.mapper.BudgetValidateRuleMapper;
import org.dromara.budget.service.IBudgetValidateService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 填报校验实现
 *
 * <p>三类校验（PRD 7.3）：
 * 1. 逻辑校验(ERROR,不可提交)：汇总行金额 = 其直系明细之和
 * 2. 合理性校验(WARN,需说明)：科目本年预算较上年实际同比变动超过 ±30%
 * 3. 完整性校验(ERROR,不可提交)：应填报科目的预算金额为空/0
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetValidateServiceImpl implements IBudgetValidateService {

    private final BudgetDataMapper budgetDataMapper;
    private final BudgetTemplateItemMapper templateItemMapper;
    private final BudgetValidateRuleMapper validateRuleMapper;

    /**
     * 从数据库加载启用的校验规则，按rule_type索引
     */
    private Map<String, BudgetValidateRule> loadEnabledRules() {
        LambdaQueryWrapper<BudgetValidateRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BudgetValidateRule::getEnabled, 1L);
        List<BudgetValidateRule> rules = validateRuleMapper.selectList(wrapper);
        Map<String, BudgetValidateRule> map = new HashMap<>();
        for (BudgetValidateRule r : rules) {
            map.put(r.getRuleType(), r);
        }
        return map;
    }

    @Override
    public BudgetValidateResult validate(BudgetValidateQuery q) {
        List<BudgetValidateResult.ValidateItem> items = new ArrayList<>();
        Map<String, BudgetValidateRule> ruleMap = loadEnabledRules();

        // 1. 加载该单位指定方案填报数据
        LambdaQueryWrapper<BudgetData> dw = new LambdaQueryWrapper<>();
        dw.eq(BudgetData::getPlanId, q.getPlanId());
        dw.eq(BudgetData::getDeptId, q.getOrgId());
        if (q.getTemplateCode() != null && !q.getTemplateCode().isEmpty()) {
            dw.eq(BudgetData::getTemplateCode, q.getTemplateCode());
        }
        List<BudgetData> data = budgetDataMapper.selectList(dw);
        Map<String, BudgetData> byItem = new HashMap<>();
        for (BudgetData d : data) {
            if (d.getItemCode() != null) {
                byItem.put(d.getTemplateCode() + "#" + d.getItemCode(), d);
            }
        }

        // 2. 基础模板科目
        List<BudgetTemplateItem> tpl = templateItemMapper.selectList(
            new LambdaQueryWrapper<BudgetTemplateItem>().eq(BudgetTemplateItem::getPlanId, 0L));

        // 3. 逻辑校验：汇总行 = Σ直系明细
        if (include("LOGIC", q.getType()) && ruleMap.containsKey("LOGIC")) {
            BudgetValidateRule logicRule = ruleMap.get("LOGIC");
            String level = logicRule.getRuleLevel() != null ? logicRule.getRuleLevel() : "ERROR";
            BigDecimal tolerance = logicRule.getThresholdValue() != null ? logicRule.getThresholdValue() : BigDecimal.valueOf(0.01);
            for (BudgetTemplateItem sum : tpl) {
                if (sum.getIsSummary() == null || sum.getIsSummary() != 1) continue;
                if (q.getTemplateCode() != null && !q.getTemplateCode().isEmpty()
                        && !q.getTemplateCode().equals(sum.getTemplateCode())) continue;
                checkSummary(sum, byItem, tpl, items, level, tolerance);
            }
        }

        // 4. 合理性校验：同比变动超阈值
        if (include("REASON", q.getType()) && ruleMap.containsKey("REASON")) {
            BudgetValidateRule reasonRule = ruleMap.get("REASON");
            String level = reasonRule.getRuleLevel() != null ? reasonRule.getRuleLevel() : "WARN";
            BigDecimal threshold = reasonRule.getThresholdValue() != null ? reasonRule.getThresholdValue() : BigDecimal.valueOf(30);
            for (BudgetTemplateItem it : tpl) {
                if (q.getTemplateCode() != null && !q.getTemplateCode().isEmpty()
                        && !q.getTemplateCode().equals(it.getTemplateCode())) continue;
                BudgetData d = byItem.get(it.getTemplateCode() + "#" + it.getItemCode());
                if (d == null || d.getBudgetAmount() == null || d.getLastActual() == null
                        || d.getLastActual().signum() == 0) continue;
                BigDecimal rate = d.getBudgetAmount().subtract(d.getLastActual())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(d.getLastActual().abs(), 2, RoundingMode.HALF_UP);
                if (rate.abs().compareTo(threshold) > 0) {
                    String dir = rate.signum() > 0 ? "增长" : "下降";
                    items.add(item("REASON", level, it.getTemplateCode(), it.getTemplateName(),
                        it.getItemCode(), it.getItemName(), "同比变动超过 ±" + threshold.stripTrailingZeros().toPlainString() + "%",
                        "「" + it.getItemName() + "」本年预算较上年实际" + dir + " " + rate.abs() + "%，需填写原因说明",
                        d.getBudgetAmount(), d.getLastActual()));
                }
            }
        }

        // 5. 完整性校验：明细科目预算为空/0
        if (include("COMPLETE", q.getType()) && ruleMap.containsKey("COMPLETE")) {
            BudgetValidateRule completeRule = ruleMap.get("COMPLETE");
            String level = completeRule.getRuleLevel() != null ? completeRule.getRuleLevel() : "ERROR";
            long missing = data.stream()
                .filter(d -> d.getBudgetAmount() == null || d.getBudgetAmount().signum() == 0)
                .count();
            if (missing > 0) {
                items.add(item("COMPLETE", level, null, null, null, null,
                    "必填科目未填报",
                    "有 " + missing + " 条科目预算金额为空或0，请补充后再提交",
                    null, null));
            }
        }

        // 汇总状态
        long error = items.stream().filter(i -> "ERROR".equals(i.getLevel())).count();
        long warn = items.stream().filter(i -> "WARN".equals(i.getLevel())).count();

        BudgetValidateResult res = new BudgetValidateResult();
        res.setStatus(error > 0 ? "BLOCK" : (warn > 0 ? "WARN" : "PASS"));
        res.setSubmittable(error == 0);
        res.setErrorCount(error);
        res.setWarnCount(warn);
        res.setItems(items);
        return res;
    }

    private void checkSummary(BudgetTemplateItem sum, Map<String, BudgetData> byItem,
                              List<BudgetTemplateItem> tpl, List<BudgetValidateResult.ValidateItem> items,
                              String level, BigDecimal tolerance) {
        BudgetData sumData = byItem.get(sum.getTemplateCode() + "#" + sum.getItemCode());
        BigDecimal actual = sumData != null && sumData.getBudgetAmount() != null
            ? sumData.getBudgetAmount() : BigDecimal.ZERO;

        BigDecimal childrenSum = BigDecimal.ZERO;
        int childCount = 0;
        for (BudgetTemplateItem it : tpl) {
            if (it.getIsSummary() != null && it.getIsSummary() == 1) continue;
            if (!String.valueOf(sum.getItemCode()).equals(String.valueOf(it.getParentCode()))) continue;
            BudgetData d = byItem.get(it.getTemplateCode() + "#" + it.getItemCode());
            if (d != null && d.getBudgetAmount() != null) {
                childrenSum = childrenSum.add(d.getBudgetAmount());
                childCount++;
            }
        }
        if (childCount == 0) {
            return;
        }
        BigDecimal diff = actual.subtract(childrenSum);
        boolean ok = diff.abs().compareTo(tolerance) <= 0;
        if (!ok) {
            items.add(item("LOGIC", level, sum.getTemplateCode(), sum.getTemplateName(),
                sum.getItemCode(), sum.getItemName(), "小计 = Σ明细项",
                "「" + sum.getItemName() + "」小计 " + actual.stripTrailingZeros().toPlainString()
                    + " 与明细合计 " + childrenSum.stripTrailingZeros().toPlainString()
                    + " 不一致（差 " + diff.stripTrailingZeros().toPlainString() + "）",
                actual, childrenSum));
        }
    }

    private boolean include(String type, String filter) {
        return filter == null || filter.isEmpty() || type.equalsIgnoreCase(filter);
    }

    private BudgetValidateResult.ValidateItem item(String type, String level, String tplCode, String tplName,
                                                   String itemCode, String itemName, String rule, String message,
                                                   BigDecimal value, BigDecimal expect) {
        BudgetValidateResult.ValidateItem i = new BudgetValidateResult.ValidateItem();
        i.setType(type);
        i.setLevel(level);
        i.setTemplateCode(tplCode);
        i.setTemplateName(tplName);
        i.setItemCode(itemCode);
        i.setItemName(itemName);
        i.setRule(rule);
        i.setMessage(message);
        i.setValue(value);
        i.setExpect(expect);
        return i;
    }
}