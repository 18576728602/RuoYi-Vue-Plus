package org.dromara.budget.domain.vo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 预算汇总看板 VO
 */
@Data
public class BudgetSummaryVo {

    /**
     * 单位汇总
     */
    @Data
    public static class DeptSummary {
        private Long deptId;
        private String deptName;
        /** 填报科目总数 */
        private Integer totalItems;
        /** 已填报科目数 */
        private Integer filledItems;
        /** 填报进度(%) */
        private BigDecimal fillProgress;
        /** 填报状态: DRAFT/SUBMITTED/APPROVED/REJECTED */
        private String status;
        /** 本年预算填报(万元): 填报但尚未审批通过 */
        private BigDecimal reportBudget;
        /** 本年预算审批(万元): 已审批通过生效 */
        private BigDecimal totalBudget;
        /** 上年实际合计(万元) */
        private BigDecimal totalActual;
        /** 增减额 */
        private BigDecimal diffAmount;
        /** 增减率(%) */
        private BigDecimal diffRate;
    }

    /**
     * 板块汇总
     */
    @Data
    public static class TemplateSummary {
        private String templateCode;
        private String templateName;
        /** 参与单位数 */
        private Integer deptCount;
        /** 已填报单位数 */
        private Integer filledDeptCount;
        /** 本年预算填报(万元): 填报但尚未审批通过 */
        private BigDecimal reportBudget;
        /** 本年预算审批(万元): 已审批通过生效 */
        private BigDecimal totalBudget;
        /** 上年实际合计 */
        private BigDecimal totalActual;
    }

    /**
     * 集团总览
     */
    @Data
    public static class Overview {
        /** 参与单位总数 */
        private Integer totalDepts;
        /** 已提交单位数 */
        private Integer submittedDepts;
        /** 已审批单位数 */
        private Integer approvedDepts;
        /** 填报进度(%) */
        private BigDecimal fillProgress;
        /** 集团本年预算总额 */
        private BigDecimal totalBudget;
        /** 集团上年实际总额 */
        private BigDecimal totalActual;
        /** 集团增减额 */
        private BigDecimal diffAmount;
        /** 集团增减率(%) */
        private BigDecimal diffRate;
    }
}
