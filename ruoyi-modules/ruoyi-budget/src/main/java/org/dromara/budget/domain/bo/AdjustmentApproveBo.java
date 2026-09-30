package org.dromara.budget.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 预算调整审批 BO
 */
@Data
public class AdjustmentApproveBo {

    /**
     * 调整记录ID
     */
    @NotNull(message = "主键不能为空")
    private Long id;

    /**
     * 审批结果: APPROVED=通过, REJECTED=驳回
     */
    @NotNull(message = "审批结果不能为空")
    private String approveResult;

    /**
     * 审批意见
     */
    private String approveRemark;
}
