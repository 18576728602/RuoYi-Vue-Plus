package org.dromara.budget.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 情景模拟参数 VO
 */
@Data
public class BudgetScenarioVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long planId;
    private String scenarioType;
    private String scenarioName;
    private BigDecimal revenueGrowth;
    private BigDecimal grossMargin;
    private BigDecimal costExpenseRate;
    private BigDecimal financingRate;
    private BigDecimal investReturnRate;
    private Integer isDefault;
    private String remark;
}