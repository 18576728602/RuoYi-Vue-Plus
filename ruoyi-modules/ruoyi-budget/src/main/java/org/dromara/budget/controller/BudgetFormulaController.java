package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.budget.domain.BudgetTemplate;
import org.dromara.budget.mapper.BudgetTemplateMapper;
import org.dromara.budget.service.IBudgetFormulaService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 预算公式计算控制器
 *
 * <p>提供公式预览计算、批量计算、语法校验等接口。
 *
 * @author Lion Li
 * @date 2026-09-29
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/formula")
public class BudgetFormulaController {

    private final IBudgetFormulaService formulaService;
    private final BudgetTemplateMapper budgetTemplateMapper;

    /**
     * 预览计算单个公式的值（配置公式时实时预览）
     *
     * @param formula      公式表达式
     * @param templateId   预算表ID（和 templateCode+budgetYear 二选一）
     * @param templateCode 预算表编码
     * @param budgetYear   预算年度
     * @param itemCode     当前科目编码（用于 SUM(children) 等相对引用）
     * @param valueField   取值字段：budgetAmount / lastActual / executionAmount
     */
    @SaCheckPermission("budget:template:query")
    @GetMapping("/preview")
    public R<BigDecimal> preview(
        @RequestParam String formula,
        @RequestParam(required = false) Long templateId,
        @RequestParam(required = false) String templateCode,
        @RequestParam(required = false) Integer budgetYear,
        @RequestParam(required = false) String itemCode,
        @RequestParam(defaultValue = "budgetAmount") String valueField) {
        Long tplId = templateId;
        Integer year = budgetYear;
        if (tplId == null && templateCode != null && budgetYear != null) {
            BudgetTemplate tpl = budgetTemplateMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BudgetTemplate>()
                    .eq(BudgetTemplate::getTemplateCode, templateCode)
                    .eq(BudgetTemplate::getBudgetYear, budgetYear)
                    .last("LIMIT 1"));
            if (tpl == null) {
                throw new ServiceException("未找到预算表：" + templateCode + " / " + budgetYear);
            }
            tplId = tpl.getId();
            year = tpl.getBudgetYear();
        }
        if (tplId == null || year == null) {
            throw new ServiceException("请指定预算表ID 或 预算表编码+年度");
        }
        BigDecimal result = formulaService.evaluate(formula, tplId, year, itemCode, valueField);
        return R.ok(result);
    }

    /**
     * 公式语法校验
     *
     * @param formula 公式表达式
     * @return msg 为 null 表示合法，否则是错误信息
     */
    @SaCheckPermission("budget:template:query")
    @GetMapping("/validate")
    public R<String> validate(@RequestParam String formula) {
        String err = formulaService.validate(formula);
        return R.ok(err == null ? "合法" : err);
    }

    /**
     * 批量计算（内部使用，按给定的 编码→公式 映射批量计算）
     */
    @SaCheckPermission("budget:template:query")
    @PostMapping("/evaluateBatch")
    public R<Map<String, BigDecimal>> evaluateBatch(
        @RequestParam Long templateId,
        @RequestParam Integer budgetYear,
        @RequestParam(defaultValue = "budgetAmount") String valueField,
        @RequestBody Map<String, String> formulaMap) {
        Map<String, BigDecimal> result = formulaService.evaluateBatch(formulaMap, templateId, budgetYear, valueField);
        return R.ok(result);
    }
}
