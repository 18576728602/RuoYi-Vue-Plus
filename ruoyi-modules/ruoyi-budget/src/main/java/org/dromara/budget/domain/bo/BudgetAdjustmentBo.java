package org.dromara.budget.domain.bo;

import org.dromara.budget.domain.BudgetAdjustment;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 预算调整记录业务对象 budget_adjustment
 *
 * @author Lion Li
 * @date 2026-08-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BudgetAdjustment.class, reverseConvertGenerate = false)
public class BudgetAdjustmentBo extends BaseEntity {

    /**
     * 主键ID
     */
    @NotNull(message = "主键ID不能为空", groups = { EditGroup.class })
    private Long id;

    /**
     * 预算方案ID
     */
    @NotNull(message = "预算方案ID不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long planId;

    /**
     * 调整单位
     */
    @NotNull(message = "调整单位不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long orgId;

    /**
     * 预算表编号
     */
    @NotBlank(message = "预算表编号不能为空", groups = { AddGroup.class, EditGroup.class })
    private String templateCode;

    /**
     * 调整科目编码
     */
    @NotBlank(message = "调整科目编码不能为空", groups = { AddGroup.class, EditGroup.class })
    private String itemCode;

    /**
     * 原预算金额
     */
    @NotNull(message = "原预算金额不能为空", groups = { AddGroup.class, EditGroup.class })
    private BigDecimal  originalAmount;

    /**
     * 调整金额(正数增加,负数减少)
     */
    @NotNull(message = "调整金额(正数增加,负数减少)不能为空", groups = { AddGroup.class, EditGroup.class })
    private BigDecimal adjustAmount;

    /**
     * 调整后金额
     */
    @NotNull(message = "调整后金额不能为空", groups = { AddGroup.class, EditGroup.class })
    private BigDecimal  adjustedAmount;

    /**
     * 调整日期
     */
    @NotNull(message = "调整日期不能为空", groups = { AddGroup.class, EditGroup.class })
    private Date adjustDate;

    /**
     * 调整原因
     */
    @NotBlank(message = "调整原因不能为空", groups = { AddGroup.class, EditGroup.class })
    private String reason;

    /**
     * 状态: PENDING=待审批, APPROVED=已通过, REJECTED=已驳回
     */
    @NotBlank(message = "状态: PENDING=待审批, APPROVED=已通过, REJECTED=已驳回不能为空", groups = { AddGroup.class, EditGroup.class })
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


}
