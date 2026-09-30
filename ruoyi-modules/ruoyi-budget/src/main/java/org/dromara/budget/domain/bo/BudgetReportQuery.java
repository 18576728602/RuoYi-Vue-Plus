package org.dromara.budget.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 预算执行分析报告查询条件
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
public class BudgetReportQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 预算方案ID
     */
    @NotNull(message = "预算方案ID不能为空")
    private Long planId;

    /**
     * 单位ID(空=集团全口径)
     */
    private Long orgId;

    /**
     * 报告期间(如 2026-09; 空=自动取当前)
     */
    private String period;
}