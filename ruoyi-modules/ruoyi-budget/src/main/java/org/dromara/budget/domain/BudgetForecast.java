package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Date;

/**
 * 滚动预测主表  budget_forecast
 *
 * @author Lion Li
 * @date 2026-09-15
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_forecast")
public class BudgetForecast extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /** 预算方案ID */
    private Long planId;

    /** 填报单位ID */
    private Long orgId;

    /** 预测版本号,如2027Q1滚动版 */
    private String forecastNo;

    /** 刷新时点:Q1/Q2/Q3/Q4 或 YTD */
    private String refreshType;

    /** 刷新日期 */
    private Date refreshDate;

    /** 预测算法:SIMPLE简单法/TREND趋势法 */
    private String forecastMethod;

    /** 说明 */
    private String remark;

    /** 生成人ID */
    private Long operatorId;

    /** 生成人姓名 */
    private String operatorName;

    /** 状态:ACTIVE正常/SUPERSEDED已覆盖 */
    private String status;
}