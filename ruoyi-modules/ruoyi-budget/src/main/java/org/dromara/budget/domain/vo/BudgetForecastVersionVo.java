package org.dromara.budget.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 滚动预测版本概览 VO
 */
@Data
public class BudgetForecastVersionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long planId;
    private Long orgId;
    private String forecastNo;
    private String refreshType;
    private Date refreshDate;
    private String forecastMethod;
    private String remark;
    private String operatorName;
    private String status;
}