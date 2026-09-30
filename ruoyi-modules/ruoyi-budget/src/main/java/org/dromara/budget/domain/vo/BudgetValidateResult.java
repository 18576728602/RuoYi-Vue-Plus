package org.dromara.budget.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 填报校验结果
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
public class BudgetValidateResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 汇总状态: PASS=通过 / BLOCK=存在错误(不可提交) / WARN=有警告(需说明原因)
     */
    private String status;

    /**
     * 是否可提交(错误>0 false)
     */
    private Boolean submittable;

    /**
     * 错误数(逻辑+完整性=不可提交)
     */
    private long errorCount;

    /**
     * 警告数(合理性=需说明)
     */
    private long warnCount;

    /**
     * 校验明细
     */
    private List<ValidateItem> items;

    @Data
    public static class ValidateItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /**
         * 类型: LOGIC / REASON / COMPLETE
         */
        private String type;

        /**
         * 级别: ERROR / WARN
         */
        private String level;

        /**
         * 预算表编号
         */
        private String templateCode;

        /**
         * 预算表名
         */
        private String templateName;

        /**
         * 科目编码/定位
         */
        private String itemCode;

        /**
         * 科目名
         */
        private String itemName;

        /**
         * 校验规则说明
         */
        private String rule;

        /**
         * 提示/描述
         */
        private String message;

        /**
         * 数值(如汇总行校验的计算值/目标值)
         */
        private BigDecimal value;

        /**
         * 目标/期望值
         */
        private BigDecimal expect;
    }
}