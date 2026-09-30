package org.dromara.budget.util;

/**
 * 填报单位显示名工具
 * <p>
 * 业务口径：
 * 1. 集团本部节点（部门名含"（本部）"）→ 显示「集团本部」
 * 2. 集团本部下的部门 → 「集团本部-部门名」，如「集团本部-综合行政部」
 * 3. 子公司（部门名含"公司"）→ 「公司名-财务部」，如「九江市工业置业有限公司-财务部」
 * 4. 其余 → 原名称
 */
public final class BudgetUnitNameUtil {

    private BudgetUnitNameUtil() {
    }

    /**
     * 依据部门名及其上级部门名生成填报单位显示名
     *
     * @param deptName     部门名称
     * @param parentName   上级部门名称（可为 null）
     * @return 格式化后的显示名
     */
    public static String format(String deptName, String parentName) {
        if (deptName == null) {
            return null;
        }
        boolean selfIsHq = isHqNode(deptName);
        if (selfIsHq) {
            return "集团本部";
        }
        boolean parentIsHq = parentName != null && isHqNode(parentName);
        if (parentIsHq) {
            return "集团本部-" + deptName;
        }
        if (deptName.contains("公司")) {
            return deptName + "-财务部";
        }
        return deptName;
    }

    private static boolean isHqNode(String name) {
        return name.contains("（本部）") || name.contains("(本部)");
    }
}