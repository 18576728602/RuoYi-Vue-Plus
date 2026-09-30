package org.dromara.budget.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算控制规则 VO
 */
@Data
public class BudgetControlRuleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long planId;
    private Long orgId;
    private String templateCode;
    private String controlStrength;
    private BigDecimal warnPercent;
    private BigDecimal blockPercent;
    private String controlScope;
    private Integer sortOrder;
    private Boolean enabled;
    private String remark;
}