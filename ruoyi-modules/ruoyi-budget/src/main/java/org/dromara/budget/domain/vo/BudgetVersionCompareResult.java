package org.dromara.budget.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 预算版本对比结果
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
public class BudgetVersionCompareResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 版本A方案名(基准)
     */
    private String planNameA;

    /**
     * 版本B方案名(对比)
     */
    private String planNameB;

    /**
     * 差异统计：变化科目数、变化总额、新增/减少金额
     */
    private CompareStat stat;

    /**
     * 对比明细行
     */
    private List<CompareRow> rows;

    /**
     * 方案A合计额度(万元)
     */
    private BigDecimal totalA;

    /**
     * 方案B合计额度(万元)
     */
    private BigDecimal totalB;

    /**
     * 合计差异(万元)
     */
    private BigDecimal diffTotal;

    @Data
    public static class CompareStat implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /**
         * 参与对比的科目数
         */
        private long totalCount;

        /**
         * 有差异的科目数
         */
        private long diffCount;

        /**
         * 金额增加的科目数
         */
        private long upCount;

        /**
         * 金额减少的科目数
         */
        private long downCount;

        /**
         * 变化绝对值总额(万元)
         */
        private BigDecimal changeTotal;

        /**
         * 净变化额(B-A,万元)
         */
        private BigDecimal netChange;
    }

    @Data
    public static class CompareRow implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /**
         * 单位名
         */
        private String orgName;

        /**
         * 单位ID
         */
        private Long orgId;

        /**
         * 预算表编号
         */
        private String templateCode;

        /**
         * 预算表名
         */
        private String templateName;

        /**
         * 科目编码
         */
        private String itemCode;

        /**
         * 科目名
         */
        private String itemName;

        /**
         * 版本A预算(万元)
         */
        private BigDecimal amountA;

        /**
         * 版本B预算(万元)
         */
        private BigDecimal amountB;

        /**
         * 差异额(B-A,万元)
         */
        private BigDecimal diff;

        /**
         * 差异率%
         */
        private BigDecimal diffRate;

        /**
         * 新增(B有A无)
         */
        private boolean added;

        /**
         * 减少(B无A有)
         */
        private boolean removed;

        /**
         * 有差异
         */
        private boolean changed;
    }
}