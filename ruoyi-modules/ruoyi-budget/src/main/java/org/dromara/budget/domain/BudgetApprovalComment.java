package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Date;

/**
 * 审批批注对象 budget_approval_comment
 *
 * <p>PRD 8.2 单元格级批注：审批人对具体预算单元格添加意见（建议修改/疑问/警告），
 * 填报人逐条查看并回复，实现精准沟通，修改痕迹全程留痕。</p>
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_approval_comment")
public class BudgetApprovalComment extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
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
}