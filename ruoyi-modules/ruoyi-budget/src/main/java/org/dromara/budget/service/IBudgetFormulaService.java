package org.dromara.budget.service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 预算公式计算服务
 *
 * <p>支持公式语法：
 * <ul>
 *   <li>{@code SUM(children)} — 递归求和当前科目的所有可编辑子孙</li>
 *   <li>{@code SUM(0201, 0202, 0203)} — 按科目编码求和（同预算表内）</li>
 *   <li>{@code SUM(02.0201, 03.0301)} — 跨表引用求和（表编码.科目编码）</li>
 *   <li>四则运算：{@code 0201 + 0202 - 0203}、{@code 0201 * 0.5}</li>
 *   <li>混合：{@code SUM(children) * 1.1}</li>
 * </ul>
 *
 * @author Lion Li
 * @date 2026-09-29
 */
public interface IBudgetFormulaService {

    /**
     * 计算单个公式的值（基于预算表挂载清单的当前填报数据）
     *
     * @param formula     公式表达式
     * @param templateId  预算表ID
     * @param budgetYear  预算年度
     * @param itemCode    当前科目编码（用于 SUM(children) 等相对引用）
     * @param valueField  取值字段：budgetAmount / lastActual / executionAmount
     * @return 计算结果
     */
    BigDecimal evaluate(String formula, Long templateId, Integer budgetYear,
                        String itemCode, String valueField);

    /**
     * 批量计算：给定一组科目编码，返回各科目按公式计算后的值
     *
     * @param formulaMap  key=科目编码, value=公式表达式
     * @param templateId  预算表ID
     * @param budgetYear  预算年度
     * @param valueField  取值字段
     * @return key=科目编码, value=计算结果
     */
    Map<String, BigDecimal> evaluateBatch(Map<String, String> formulaMap,
                                           Long templateId, Integer budgetYear,
                                           String valueField);

    /**
     * 公式语法校验（仅校验格式是否合法，不做实际计算）
     *
     * @param formula 公式表达式
     * @return 校验结果：null=合法，否则返回错误信息
     */
    String validate(String formula);
}
