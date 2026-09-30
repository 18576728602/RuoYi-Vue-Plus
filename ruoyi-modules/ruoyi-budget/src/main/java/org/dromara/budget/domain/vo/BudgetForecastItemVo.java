package org.dromara.budget.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 滚动预测明细行 VO（含实际/预测各季度）
 */
@Data
public class BudgetForecastItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String templateCode;
    private String templateName;
    private String itemCode;
    private String itemName;
    private String parentCode;
    private Integer isSummary;

    private BigDecimal budgetAmount;
    private BigDecimal actualQ1;
    private BigDecimal actualQ2;
    private BigDecimal actualQ3;
    private BigDecimal actualQ4;
    private BigDecimal actualTotal;

    private BigDecimal forecastQ1;
    private BigDecimal forecastQ2;
    private BigDecimal forecastQ3;
    private BigDecimal forecastQ4;
    private BigDecimal forecastTotal;
    private BigDecimal deviation;

    /** 预测执行率 = 预测/预算 % */
    private BigDecimal forecastRate;
    /** 实际执行率 = 实际/预算 % */
    private BigDecimal actualRate;
}