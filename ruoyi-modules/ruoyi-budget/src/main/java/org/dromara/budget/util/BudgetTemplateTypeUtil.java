package org.dromara.budget.util;

import cn.hutool.core.util.StrUtil;

import java.util.Set;

/**
 * 预算表类型工具：业务口径判断统一走「表类型」而非散落的 magic template_code。
 * <p>表类型与编码关系（budget_template.template_type，UI/新增/复制均写入）：
 * <ul>
 *   <li>BASE   = 基础表(默认，普通填报)</li>
 *   <li>TEXT   = 文本表(01 公司基本信息)，不参与数值汇总计算</li>
 *   <li>SPECIAL= 特种表(13 平衡校验 / 14 计算行 / 16 三公合计)，按类型驱动特殊逻辑</li>
 * </ul>
 * 其中仍需按具体表区分的细口径（如 16 是否敏感、12/13/16 是否报表映射）在各自场景显式判断。
 */
public final class BudgetTemplateTypeUtil {

    public static final String BASE = "BASE";
    public static final String TEXT = "TEXT";
    public static final String SPECIAL = "SPECIAL";

    /** 文本表编码集合(当前为 B01 公司基本信息，不参与数值汇总) */
    private static final Set<String> TEXT_CODES = Set.of("B01");

    /** 特种表编码集合(B13 平衡校验 / B14 计算行 / B16 三公合计) */
    private static final Set<String> SPECIAL_CODES = Set.of("B13", "B14", "B16");

    /** 预算表编码前缀(序列规则：B + 两位序号) */
    public static final String CODE_PREFIX = "B";

    private BudgetTemplateTypeUtil() {
    }

    /** 该表是否为文本表(不参与数值汇总) */
    public static boolean isText(String templateCode) {
        return StrUtil.isNotBlank(templateCode) && TEXT_CODES.contains(templateCode);
    }

    /** 该表是否为特种表(平衡/计算/三公合计由系统驱动) */
    public static boolean isSpecial(String templateCode) {
        return StrUtil.isNotBlank(templateCode) && SPECIAL_CODES.contains(templateCode);
    }

    /** 文本表编码集合(供 SQL 过滤 not-in 使用) */
    public static Set<String> textCodes() {
        return TEXT_CODES;
    }

    /** 特种表编码集合 */
    public static Set<String> specialCodes() {
        return SPECIAL_CODES;
    }
}