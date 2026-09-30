package org.dromara.budget.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 滚动预测生成/查询条件
 *
 * @author Lion Li
 * @date 2026-09-15
 */
@Data
public class BudgetForecastQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 预算方案ID
     */
    @NotNull(message = "预算方案不能为空")
    private Long planId;

    /**
     * 单位ID(空=默认当前登录单位/集团可取全集团)
     */
    private Long orgId;

    /**
     * 刷新时点:Q1/Q2/Q3/Q4 或 YTD
     */
    @NotNull(message = "刷新时点不能为空")
    private String refreshType;

    /**
     * 预测算法:SIMPLE简单法/TREND趋势法
     */
    private String forecastMethod;
}