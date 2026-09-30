package org.dromara.budget.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 敏感性分析行 VO（龙卷风图）
 */
@Data
public class BudgetSensitivityRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 变动因素 */
    private String factor;

    /** 变动幅度（如 +5% / -5%），负向影响 */
    private String changeRange;

    /** 净利润影响金额 */
    private BigDecimal impact;

    /** 敏感程度（1-5） */
    private Integer degree;
}