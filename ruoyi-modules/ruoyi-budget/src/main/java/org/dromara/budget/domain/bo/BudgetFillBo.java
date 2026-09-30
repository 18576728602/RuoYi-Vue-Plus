package org.dromara.budget.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 预算填报批量保存业务对象
 */
@Data
public class BudgetFillBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 预算方案ID
     */
    @NotNull(message = "预算方案不能为空")
    private Long planId;

    /**
     * 组织ID
     */
    @NotNull(message = "组织不能为空")
    private Long orgId;

    /**
     * 部门ID（admin用户传入，普通用户自动获取）
     */
    private Long deptId;

    /**
     * 预算表编号（保存单表时传，保存全部时可不传）
     */
    private String templateCode;

    /**
     * 数据版本: BUDGET=预算数, ACTUAL=上年实际, EXECUTION=执行数
     */
    private String dataVersion;

    /**
     * 提交说明（存在合理性 WARN 警告时填写，用于留痕）
     */
    private String submitReason;

    /**
     * 填报数据列表
     */
    @NotEmpty(message = "填报数据不能为空")
    private List<FillItemBo> items;

    /**
     * 单条填报数据
     */
    @Data
    public static class FillItemBo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 填报数据ID（已有数据时传，新增时为null）
         */
        private Long id;

        /**
         * 科目编码
         */
        @NotBlank(message = "科目编码不能为空")
        private String itemCode;

        /**
         * 预算表编号
         */
        @NotNull(message = "预算表编号不能为空")
        private String templateCode;

        /**
         * 预算金额
         */
        private BigDecimal budgetAmount;

        /**
         * 上年实际金额
         */
        private BigDecimal lastActual;

        /**
         * 填报说明
         */
        private String remark;
    }
}
