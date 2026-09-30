package org.dromara.budget.util;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 预算汇总行公式解析引擎
 * 支持格式：
 *   SUM(children)          — 递归求和所有子孙可编辑行
 *   SUM(1601,1602,1603)    — 求和指定科目编码（同表内）
 *   SUM(02.0101,03.0201)   — 跨表求和（表编码.科目编码）
 */
public class FormulaEngine {

    private static final Pattern SUM_PATTERN = Pattern.compile(
        "^SUM\\((.+)\\)$", Pattern.CASE_INSENSITIVE);

    /**
     * 解析并计算公式
     *
     * @param formula           公式字符串，如 "SUM(1601,1602,1603)" 或 "SUM(children)"
     * @param currentTemplateCode 当前科目所属预算表编码
     * @param currentItemCode   当前科目编码
     * @param dataLookup        数据查找函数：传入 (templateCode, itemCode) 返回 [budgetAmount, lastActual]
     * @param childrenLookup    子孙查找函数：传入 (templateCode, itemCode) 返回所有子孙可编辑行的 itemCode 列表
     * @return [budgetAmount, lastActual] 计算结果，返回 null 表示无法计算
     */
    public static BigDecimal[] evaluate(
        String formula,
        String currentTemplateCode,
        String currentItemCode,
        DataLookup dataLookup,
        ChildrenLookup childrenLookup
    ) {
        if (formula == null || formula.trim().isEmpty()) return null;

        formula = formula.trim();

        Matcher m = SUM_PATTERN.matcher(formula);
        if (!m.matches()) return null;

        String args = m.group(1).trim();

        // SUM(children) — 递归求和所有子孙可编辑行
        if ("children".equalsIgnoreCase(args)) {
            List<String> childCodes = childrenLookup.getChildren(currentTemplateCode, currentItemCode);
            if (childCodes == null || childCodes.isEmpty()) return null;

            BigDecimal sumBudget = BigDecimal.ZERO;
            BigDecimal sumActual = BigDecimal.ZERO;
            boolean found = false;
            for (String code : childCodes) {
                BigDecimal[] vals = dataLookup.get(currentTemplateCode, code);
                if (vals != null) {
                    if (vals[0] != null) { sumBudget = sumBudget.add(vals[0]); found = true; }
                    if (vals[1] != null) { sumActual = sumActual.add(vals[1]); found = true; }
                }
            }
            return found ? new BigDecimal[]{sumBudget, sumActual} : null;
        }

        // SUM(code1,code2,...) 或 SUM(table.code,table.code,...)
        String[] parts = args.split(",");
        BigDecimal sumBudget = BigDecimal.ZERO;
        BigDecimal sumActual = BigDecimal.ZERO;
        boolean found = false;

        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            String tplCode;
            String itemCode;

            if (part.contains(".")) {
                // 跨表引用：表编码.科目编码
                int dotIdx = part.indexOf('.');
                tplCode = part.substring(0, dotIdx);
                itemCode = part.substring(dotIdx + 1);
            } else {
                // 同表引用
                tplCode = currentTemplateCode;
                itemCode = part;
            }

            BigDecimal[] vals = dataLookup.get(tplCode, itemCode);
            if (vals != null) {
                if (vals[0] != null) { sumBudget = sumBudget.add(vals[0]); found = true; }
                if (vals[1] != null) { sumActual = sumActual.add(vals[1]); found = true; }
            }
        }

        return found ? new BigDecimal[]{sumBudget, sumActual} : null;
    }

    @FunctionalInterface
    public interface DataLookup {
        BigDecimal[] get(String templateCode, String itemCode);
    }

    @FunctionalInterface
    public interface ChildrenLookup {
        List<String> getChildren(String templateCode, String parentItemCode);
    }
}
