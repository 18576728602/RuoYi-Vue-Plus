package org.dromara.budget.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 预算执行差异归因结果
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
public class BudgetAnalysisResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 归因维度: UNIT/ITEM
     */
    private String dimension;

    /**
     * 概览卡
     */
    private BudgetAnalysisOverview overview;

    /**
     * 归因明细行(按差异额贡献度排序, 负数超支在前)
     */
    private List<BudgetAnalysisRow> rows;

    /**
     * 预算方案ID
     */
    private Long planId;
}