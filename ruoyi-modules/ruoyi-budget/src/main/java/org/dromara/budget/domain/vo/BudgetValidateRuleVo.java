package org.dromara.budget.domain.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class BudgetValidateRuleVo {

    private Long id;
    private String ruleType;
    private String ruleName;
    private String ruleLevel;
    private Long enabled;
    private BigDecimal thresholdValue;
    private Integer sortOrder;
    private String description;
}
