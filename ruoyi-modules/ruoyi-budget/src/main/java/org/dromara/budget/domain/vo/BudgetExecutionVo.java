package org.dromara.budget.domain.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算执行情况 VO
 */
@Data
public class BudgetExecutionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long planId;
    private String planName;
    private Long orgId;
    private String orgName;
    private Long deptId;
    private String templateCode;
    private String templateName;
    private String itemCode;
    private String itemName;
    private String parentCode;
    private Long itemLevel;
    private Long itemOrder;
    private Long isSummary;
    private Long isEditable;

    private BigDecimal budgetAmount;
    private BigDecimal executionAmount;
    private BigDecimal lastActual;
    private BigDecimal executionRate;
    private BigDecimal deviation;

    /**
     * 第一季度执行金额
     */
    private BigDecimal q1Amount;

    /**
     * 第二季度执行金额
     */
    private BigDecimal q2Amount;

    /**
     * 第三季度执行金额
     */
    private BigDecimal q3Amount;

    /**
     * 第四季度执行金额
     */
    private BigDecimal q4Amount;

    private String status;
    private String remark;

    /**
     * 事前控制等级: GREEN/YELLOW/RED(空或NONE=未配置规则/不控制)
     */
    private String controlLevel;

    /**
     * 事前控制提示文案
     */
    private String controlMessage;
}
