package org.dromara.budget.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算事前控制评估结果（供执行列表/录入前置拦截使用）
 */
@Data
public class BudgetControlResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 控制等级: GREEN=正常 / YELLOW=提示 / RED=超标
     */
    private String level;

    /**
     * 是否阻断提交(仅刚性控制且达控制阈值时为 true)
     */
    private boolean blocked;

    /**
     * 执行率%
     */
    private BigDecimal rate;

    /**
     * 控制参考的预算额度
     */
    private BigDecimal budget;

    /**
     * 控制参考的执行金额
     */
    private BigDecimal execution;

    /**
     * 命中的控制强度
     */
    private String strength;

    /**
     * 面向用户的提示文案
     */
    private String message;

    public static BudgetControlResult of(String level, boolean blocked, BigDecimal rate,
                                          BigDecimal budget, BigDecimal execution,
                                          String strength, String message) {
        BudgetControlResult r = new BudgetControlResult();
        r.setLevel(level);
        r.setBlocked(blocked);
        r.setRate(rate);
        r.setBudget(budget);
        r.setExecution(execution);
        r.setStrength(strength);
        r.setMessage(message);
        return r;
    }
}