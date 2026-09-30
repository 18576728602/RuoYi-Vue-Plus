package org.dromara.budget.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.dromara.budget.domain.BudgetApprovalComment;

import java.io.Serial;
import java.io.Serializable;

/**
 * 审批批注新增对象
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
@AutoMapper(target = BudgetApprovalComment.class, reverseConvertGenerate = false)
public class BudgetApprovalCommentBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 关联审批记录ID（budget_approval_record.id），按轮次隔离
     */
    private Long recordId;

    /**
     * 关联类型 FILL=预算填报 / ADJUSTMENT=预算调整
     */
    @NotBlank(message = "关联类型不能为空")
    private String targetType;

    /**
     * 预算方案ID
     */
    @NotNull(message = "预算方案ID不能为空")
    private Long planId;

    /**
     * 填报单位(公司)ID
     */
    @NotNull(message = "填报单位不能为空")
    private Long deptId;

    /**
     * 预算表编号
     */
    @NotBlank(message = "预算表编号不能为空")
    private String templateCode;

    /**
     * 科目编码
     */
    private String itemCode;

    /**
     * 意见类型 SUGGESTION建议修改 / QUESTION疑问 / WARNING警告
     */
    @NotBlank(message = "意见类型不能为空")
    private String commentType;

    /**
     * 意见内容
     */
    @NotBlank(message = "意见内容不能为空")
    private String content;
}