package org.dromara.budget.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import org.dromara.budget.domain.BudgetApprovalComment;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 审批批注视图对象
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BudgetApprovalComment.class)
public class BudgetApprovalCommentVo implements Serializable {

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
    private String targetType;

    /**
     * 预算方案ID
     */
    private Long planId;

    /**
     * 填报单位(公司)ID
     */
    private Long deptId;

    /**
     * 预算表编号
     */
    private String templateCode;

    /**
     * 科目编码
     */
    private String itemCode;

    /**
     * 意见类型 SUGGESTION建议修改 / QUESTION疑问 / WARNING警告
     */
    private String commentType;

    /**
     * 意见内容
     */
    private String content;

    /**
     * 评论人ID
     */
    private Long commenterId;

    /**
     * 评论人名称
     */
    private String commenterName;

    /**
     * 回复内容
     */
    private String replyContent;

    /**
     * 回复人ID
     */
    private Long replierId;

    /**
     * 回复人名称
     */
    private String replierName;

    /**
     * 回复时间
     */
    private Date replyTime;

    /**
     * 状态 PENDING待处理 / HANDLED已处理 / ACCEPTED已采纳
     */
    private String status;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 科目名称（联表补充）
     */
    private String itemName;

    /**
     * 意见类型文本
     */
    private String commentTypeLabel;

    /**
     * 状态文本
     */
    private String statusLabel;

    /**
     * 提交轮次（关联审批记录）
     */
    private Integer submitRound;

    /**
     * 审批记录状态（关联审批记录）
     */
    private String recordStatus;

    /**
     * 审批记录提交时间（关联审批记录）
     */
    private Date recordSubmitTime;

    /**
     * 审批记录审批人（关联审批记录）
     */
    private String recordApproverName;

    /**
     * 审批记录审批时间（关联审批记录）
     */
    private Date recordApproveTime;
}