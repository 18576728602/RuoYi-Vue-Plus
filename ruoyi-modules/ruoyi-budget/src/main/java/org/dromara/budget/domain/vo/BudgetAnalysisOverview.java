package org.dromara.budget.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算执行差异归因行/概览
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
public class BudgetAnalysisOverview implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 预算数合计(万元)
     */
    private BigDecimal budgetTotal;

    /**
     * 执行数合计(万元)
     */
    private BigDecimal execTotal;

    /**
     * 差异额(万元, 预算-执行；正=结余, 负=超支)
     */
    private BigDecimal diffTotal;

    /**
     * 差异率%
     */
    private BigDecimal diffRate;

    /**
     * 平均执行率%
     */
    private BigDecimal execRate;

    /**
     * 涉及单位数
     */
    private Long orgCount;

    /**
     * 涉及科目数(明细)
     */
    private Long itemCount;
}