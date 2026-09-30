package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 滚动预测明细表  budget_forecast_item
 *
 * @author Lion Li
 * @date 2026-09-15
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_forecast_item")
public class BudgetForecastItem extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /** 预测主表ID */
    private Long forecastId;

    /** 预算方案ID */
    private Long planId;

    /** 填报单位ID */
    private Long orgId;

    /** 预算表编号 */
    private String templateCode;

    /** 科目编码 */
    private String itemCode;

    /** 科目名称 */
    private String itemName;

    /** 父级科目 */
    private String parentCode;

    /** 是否汇总行 */
    private Integer isSummary;

    /** 当年预算数 */
    private BigDecimal budgetAmount;

    /** Q1实际 */
    private BigDecimal actualQ1;

    /** Q2实际 */
    private BigDecimal actualQ2;

    /** Q3实际 */
    private BigDecimal actualQ3;

    /** Q4实际 */
    private BigDecimal actualQ4;

    /** 已实现实际合计 */
    private BigDecimal actualTotal;

    /** Q1预测(含已过季=实际) */
    private BigDecimal forecastQ1;

    /** Q2预测 */
    private BigDecimal forecastQ2;

    /** Q3预测 */
    private BigDecimal forecastQ3;

    /** Q4预测 */
    private BigDecimal forecastQ4;

    /** 全年预测合计 */
    private BigDecimal forecastTotal;

    /** 预测-预算差异 */
    private BigDecimal deviation;
}