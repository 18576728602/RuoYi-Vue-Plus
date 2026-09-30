package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 填报校验规则配置 budget_validate_rule
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_validate_rule")
public class BudgetValidateRule extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    private String ruleType;

    private String ruleName;

    private String ruleLevel;

    private Long enabled;

    private BigDecimal thresholdValue;

    private Integer sortOrder;

    private String description;
}
