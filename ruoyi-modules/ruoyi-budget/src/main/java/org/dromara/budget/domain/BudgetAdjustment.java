package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serial;

/**
 * 预算调整记录对象 budget_adjustment
 *
 * @author Lion Li
 * @date 2026-08-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_adjustment")
public class BudgetAdjustment extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 预算方案ID
     */
    private Long planId;

    /**
     * 调整单位
     */
    private Long orgId;

    /**
     * 预算表编号
     */
    private String templateCode;

    /**
     * 调整科目编码
     */
    private String itemCode;

    /**
     * 原预算金额
     */
    private BigDecimal originalAmount;

    /**
     * 调整金额(正数增加,负数减少)
     */
    private BigDecimal adjustAmount;

    /**
     * 调整后金额
     */
    private BigDecimal adjustedAmount;

    /**
     * 调整日期
     */
    private Date adjustDate;

    /**
     * 调整原因
     */
    private String reason;

    /**
     * 状态: PENDING=待审批, APPROVED=已通过, REJECTED=已驳回
     */
    private String status;

    /**
     * 申请人
     */
    private Long applicantId;

    /**
     * 申请人姓名
     */
    private String applicantName;

    /**
     * 审批人
     */
    private Long approverId;

    /**
     * 审批人姓名
     */
    private String approverName;

    /**
     * 审批时间
     */
    private Date approveTime;

    /**
     * 审批意见
     */
    private String approveRemark;

    /**
     * 乐观锁版本号
     */
    @Version
    private Long version;

    /**
     * 删除标志: 0=正常, 其他=已删除
     */
    @TableLogic
    private Long delFlag;


}
