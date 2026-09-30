package org.dromara.budget.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class BudgetValidateRuleBo {

    private Long id;

    @NotBlank(message = "校验类型不能为空")
    private String ruleType;

    @NotBlank(message = "规则名称不能为空")
    private String ruleName;

    @NotBlank(message = "规则级别不能为空")
    private String ruleLevel;

    private Long enabled;

    private BigDecimal thresholdValue;

    private Integer sortOrder;

    private String description;
}
