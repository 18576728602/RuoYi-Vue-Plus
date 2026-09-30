package org.dromara.budget.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 滚动预测详情 VO（单版本，含趋势图数据与汇总）
 */
@Data
public class BudgetForecastDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long planId;
    private String planName;
    private Long orgId;
    private String orgName;
    private String forecastNo;
    private String refreshType;
    private String forecastMethod;

    // 全年汇总金额
    private BigDecimal totalBudget;
    private BigDecimal totalActual;
    private BigDecimal totalForecast;
    private BigDecimal totalDeviation;
    private BigDecimal forecastRate;

    // 趋势图数据（按季度：实际点 / 预测线 / 预算线）
    private List<String> quarters;
    private List<BigDecimal> budgetLine;
    private List<BigDecimal> actualLine;
    private List<BigDecimal> forecastLine;

    // 明细行
    private List<BudgetForecastItemVo> items = new ArrayList<>();
}