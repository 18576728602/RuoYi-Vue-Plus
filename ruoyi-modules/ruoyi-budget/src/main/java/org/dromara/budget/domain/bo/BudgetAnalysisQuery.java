package org.dromara.budget.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 预算执行差异分析查询条件
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
public class BudgetAnalysisQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 预算方案ID
     */
    @NotNull(message = "预算方案ID不能为空")
    private Long planId;

    /**
     * 归因维度: UNIT=按单位 / ITEM=按科目
     */
    @NotNull(message = "归因维度不能为空")
    private String dimension;

    /**
     * 预算表编号筛选(空=全部表)
     */
    private String templateCode;

    /**
     * 单位筛选(空=全部单位)
     */
    private Long orgId;

    /**
     * 执行率异动阈值%(差异归因界面用于高亮异常科目,默认80)
     */
    private Integer threshold;
}