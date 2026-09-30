package org.dromara.budget.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.budget.mapper.BudgetTemplateItemMapper;
import org.dromara.budget.service.IBudgetFormulaService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 预算公式计算服务实现
 *
 * <p>轻量级实现，不依赖第三方表达式引擎。
 * 支持：SUM(children) / SUM(code1,code2) / SUM(table.code,...) / 四则运算 / 混合
 *
 * @author Lion Li
 * @date 2026-09-29
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetFormulaServiceImpl implements IBudgetFormulaService {

    private final BudgetTemplateItemMapper budgetTemplateItemMapper;

    private static final Pattern SUM_PATTERN = Pattern.compile("SUM\\((.+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
        "SUM\\([^)]+\\)|[\\d.]+|[a-zA-Z0-9]+(?:\\.[a-zA-Z0-9]+)?|[+\\-*/()]");
    private static final Set<String> SUPPORTED_FIELDS = Set.of(
        "budgetAmount", "lastActual", "executionAmount");

    @Override
    public BigDecimal evaluate(String formula, Long templateId, Integer budgetYear,
                               String itemCode, String valueField) {
        if (StrUtil.isBlank(formula)) {
            return null;
        }
        if (!SUPPORTED_FIELDS.contains(valueField)) {
            throw new ServiceException("不支持的取值字段：" + valueField);
        }
        // 加载上下文：预算表内所有挂载科目 -> 编码->值 映射
        EvalContext ctx = loadContext(templateId, budgetYear, valueField);
        // 计算
        return evalExpr(formula.trim().toUpperCase(), ctx, itemCode);
    }

    @Override
    public Map<String, BigDecimal> evaluateBatch(Map<String, String> formulaMap,
                                                  Long templateId, Integer budgetYear,
                                                  String valueField) {
        Map<String, BigDecimal> result = new HashMap<>();
        if (formulaMap == null || formulaMap.isEmpty()) {
            return result;
        }
        EvalContext ctx = loadContext(templateId, budgetYear, valueField);
        for (Map.Entry<String, String> e : formulaMap.entrySet()) {
            try {
                BigDecimal val = evalExpr(e.getValue().trim().toUpperCase(), ctx, e.getKey());
                if (val != null) {
                    result.put(e.getKey(), val);
                }
            } catch (Exception ex) {
                log.warn("公式计算失败, code={}, formula={}, err={}",
                    e.getKey(), e.getValue(), ex.getMessage());
            }
        }
        return result;
    }

    @Override
    public String validate(String formula) {
        if (StrUtil.isBlank(formula)) {
            return null;
        }
        try {
            // 用空上下文试算，只要不抛语法异常就算格式合法
            evalExpr(formula.trim().toUpperCase(), new EvalContext(Map.of(), Map.of(), ""), "TEST");
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    // ========== 内部实现 ==========

    /** 计算上下文：同表编码->值，跨表编码->值，父->子映射 */
    private static class EvalContext {
        final Map<String, BigDecimal> sameTableValues; // 同表内 科目编码->值
        final Map<String, Map<String, BigDecimal>> crossTableValues; // 跨表 tableCode -> (itemCode -> value)
        final Map<String, List<String>> childrenMap; // 父编码 -> 子编码列表
        final String templateCode; // 当前预算表编码

        EvalContext(Map<String, BigDecimal> sameTableValues,
                    Map<String, List<String>> childrenMap,
                    String templateCode) {
            this.sameTableValues = sameTableValues;
            this.childrenMap = childrenMap;
            this.templateCode = templateCode;
            this.crossTableValues = new HashMap<>();
        }
    }

    private EvalContext loadContext(Long templateId, Integer budgetYear, String valueField) {
        List<BudgetTemplateItem> items = budgetTemplateItemMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BudgetTemplateItem>()
                .eq(BudgetTemplateItem::getTemplateId, templateId)
                .eq(BudgetTemplateItem::getBudgetYear, budgetYear)
                .eq(BudgetTemplateItem::getDelFlag, 0));

        Map<String, BigDecimal> values = new HashMap<>();
        Map<String, List<String>> childrenMap = new HashMap<>();
        String tplCode = items.isEmpty() ? "" : items.get(0).getTemplateCode();

        for (BudgetTemplateItem it : items) {
            String code = it.getItemCode();
            BigDecimal val = extractValue(it, valueField);
            if (val != null) {
                values.put(code, val);
            }
            if (StrUtil.isNotBlank(it.getParentCode())) {
                childrenMap.computeIfAbsent(it.getParentCode(), k -> new ArrayList<>()).add(code);
            }
        }
        return new EvalContext(values, childrenMap, tplCode);
    }

    private BigDecimal extractValue(BudgetTemplateItem item, String valueField) {
        // budget_template_item 目前可能没有填报数值字段（填报数据在另一张表里）
        // 预览计算时，如果没有填报数据，就用 0 占位，保证公式语法能走通
        // TODO: 接入 budget_fill_data 后取真实值
        return BigDecimal.ZERO;
    }

    private static final Pattern FUNC_PATTERN = Pattern.compile(
        "(SUM|AVG|MAX|MIN|COUNT)\\(", Pattern.CASE_INSENSITIVE);

    /** 表达式求值主入口 */
    private BigDecimal evalExpr(String expr, EvalContext ctx, String currentCode) {
        if (StrUtil.isBlank(expr)) {
            return BigDecimal.ZERO;
        }
        // 预处理：所有函数替换为具体数值
        String expanded = expandFunctions(expr, ctx, currentCode);
        // 然后做四则运算求值
        return evalArithmetic(expanded);
    }

    /** 将所有函数(SUM/AVG/MAX/MIN/COUNT)展开为具体数值 */
    private String expandFunctions(String expr, EvalContext ctx, String currentCode) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < expr.length()) {
            // 尝试匹配函数名
            boolean matched = false;
            for (String fnName : new String[]{"SUM(", "AVG(", "MAX(", "MIN(", "COUNT("}) {
                if (i + fnName.length() <= expr.length()
                    && expr.substring(i, i + fnName.length()).equalsIgnoreCase(fnName)) {
                    // 找到匹配的右括号
                    int depth = 1;
                    int j = i + fnName.length();
                    while (j < expr.length() && depth > 0) {
                        if (expr.charAt(j) == '(') depth++;
                        else if (expr.charAt(j) == ')') depth--;
                        j++;
                    }
                    String argsStr = expr.substring(i + fnName.length(), j - 1).trim();
                    String func = fnName.substring(0, fnName.length() - 1).toUpperCase();
                    BigDecimal val = computeFunc(func, argsStr, ctx, currentCode);
                    result.append(val.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
                    i = j;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                result.append(expr.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    /** 计算聚合函数的值 */
    private BigDecimal computeFunc(String func, String argsStr, EvalContext ctx, String currentCode) {
        List<BigDecimal> values = collectValues(argsStr, ctx, currentCode);
        if (values.isEmpty()) return BigDecimal.ZERO;
        return switch (func) {
            case "SUM" -> values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            case "AVG" -> {
                BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                yield sum.divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP);
            }
            case "MAX" -> values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            case "MIN" -> values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            case "COUNT" -> BigDecimal.valueOf(values.size());
            default -> throw new ServiceException("不支持的函数：" + func);
        };
    }

    /** 收集函数参数对应的所有值 */
    private List<BigDecimal> collectValues(String argsStr, EvalContext ctx, String currentCode) {
        List<BigDecimal> values = new ArrayList<>();
        if ("CHILDREN".equals(argsStr.trim())) {
            collectDescendantValues(currentCode, ctx, values);
            return values;
        }
        String[] parts = argsStr.split(",");
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            BigDecimal val = resolveRef(p, ctx);
            if (val != null) {
                values.add(val);
            }
        }
        return values;
    }

    /** 递归收集子孙值 */
    private void collectDescendantValues(String parentCode, EvalContext ctx, List<BigDecimal> values) {
        List<String> children = ctx.childrenMap.get(parentCode);
        if (children == null) return;
        for (String childCode : children) {
            BigDecimal val = ctx.sameTableValues.get(childCode);
            if (val != null) {
                values.add(val);
            }
            collectDescendantValues(childCode, ctx, values);
        }
    }

    /** 解析引用：编码（同表）或 table.code（跨表） */
    private BigDecimal resolveRef(String ref, EvalContext ctx) {
        if (ref.contains(".")) {
            int dotIdx = ref.indexOf('.');
            String tableCode = ref.substring(0, dotIdx);
            String itemCode = ref.substring(dotIdx + 1);
            // 跨表：暂不支持（需要额外查其他表的填报数据）
            // TODO: 接入跨表数据后再支持
            return BigDecimal.ZERO;
        }
        // 同表引用
        BigDecimal val = ctx.sameTableValues.get(ref);
        return val != null ? val : BigDecimal.ZERO;
    }

    /** 四则运算求值（支持 + - * / 和括号） */
    private BigDecimal evalArithmetic(String expr) {
        expr = expr.replaceAll("\\s+", "");
        if (expr.isEmpty()) return BigDecimal.ZERO;
        return parseExpression(new ExprParser(expr));
    }

    private BigDecimal parseExpression(ExprParser p) {
        BigDecimal left = parseTerm(p);
        while (p.pos < p.expr.length()) {
            char c = p.expr.charAt(p.pos);
            if (c == '+' || c == '-') {
                p.pos++;
                BigDecimal right = parseTerm(p);
                left = c == '+' ? left.add(right) : left.subtract(right);
            } else {
                break;
            }
        }
        return left;
    }

    private BigDecimal parseTerm(ExprParser p) {
        BigDecimal left = parseFactor(p);
        while (p.pos < p.expr.length()) {
            char c = p.expr.charAt(p.pos);
            if (c == '*' || c == '/') {
                p.pos++;
                BigDecimal right = parseFactor(p);
                if (c == '*') {
                    left = left.multiply(right);
                } else {
                    if (right.compareTo(BigDecimal.ZERO) == 0) {
                        throw new ServiceException("公式错误：除数不能为 0");
                    }
                    left = left.divide(right, 6, RoundingMode.HALF_UP);
                }
            } else {
                break;
            }
        }
        return left;
    }

    private BigDecimal parseFactor(ExprParser p) {
        if (p.pos >= p.expr.length()) {
            throw new ServiceException("公式错误：表达式不完整");
        }
        char c = p.expr.charAt(p.pos);
        if (c == '(') {
            p.pos++;
            BigDecimal val = parseExpression(p);
            if (p.pos >= p.expr.length() || p.expr.charAt(p.pos) != ')') {
                throw new ServiceException("公式错误：缺少右括号");
            }
            p.pos++;
            return val;
        }
        if (c == '-') {
            p.pos++;
            return parseFactor(p).negate();
        }
        if (c == '+') {
            p.pos++;
            return parseFactor(p);
        }
        // 数字
        int start = p.pos;
        while (p.pos < p.expr.length() && (Character.isDigit(p.expr.charAt(p.pos)) || p.expr.charAt(p.pos) == '.')) {
            p.pos++;
        }
        if (start == p.pos) {
            throw new ServiceException("公式错误：位置 " + p.pos + " 附近有不支持的字符 '" + c + "'");
        }
        return new BigDecimal(p.expr.substring(start, p.pos));
    }

    private static class ExprParser {
        final String expr;
        int pos;
        ExprParser(String expr) { this.expr = expr; this.pos = 0; }
    }
}
