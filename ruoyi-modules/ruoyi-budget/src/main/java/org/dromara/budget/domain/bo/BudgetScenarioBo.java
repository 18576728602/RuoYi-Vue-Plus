package org.dromara.budget.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 情景模拟参数保存/更新
 *
 * @author Lion Li
 * @date 2026-09-15
 */
@Data
public class BudgetScenarioBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID(更新时传入)
     */
    private Long id;

    /**
     * 预算方案ID
     */
    @NotNull(message = "预算方案不能为空")
    private Long planId;

    /**
     * 情景类型:BASE基准/OPTIMISTIC乐观/PESSIMISTIC悲观/CUSTOM自定义
     */
    @NotBlank(message = "情景类型不能为空")
    private String scenarioType;

    /**
     * 情景名称
     */
    @NotBlank(message = "情景名称不能为空")
    private String scenarioName;

    /**
     * 收入增长率(%)
     */
    private BigDecimal revenueGrowth;

    /**
     * 毛利率(%)
     */
    private BigDecimal grossMargin;

    /**
     * 成本费用率(%)
     */
    private BigDecimal costExpenseRate;

    /**
     * 融资成本/利率(%)
     */
    private BigDecimal financingRate;

    /**
     * 投资回报率(%)
     */
    private BigDecimal investReturnRate;

    /**
     * 是否默认情景
     */
    private Integer isDefault;

    /**
     * 说明
     */
    private String remark;
}