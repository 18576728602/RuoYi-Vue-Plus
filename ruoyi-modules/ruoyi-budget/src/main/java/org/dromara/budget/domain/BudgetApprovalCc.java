package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Date;

/**
 * 审批抄送记录对象 budget_approval_cc
 *
 * <p>审批人将预算填报/调整的审批单抄送给其他用户知悉，被抄送人可查看，不参与审批。</p>
 *
 * @author Lion Li
 * @date 2026-09-15
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_approval_cc")
public class BudgetApprovalCc extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

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
     * 抄送人ID
     */
    private Long senderId;

    /**
     * 抄送人名称
     */
    private String senderName;

    /**
     * 被抄送人ID
     */
    private Long receiverId;

    /**
     * 被抄送人名称
     */
    private String receiverName;

    /**
     * 抄送说明
     */
    private String reason;

    /**
     * 是否已读:0=未读,1=已读
     */
    private Integer isRead;

    /**
     * 已读时间
     */
    private Date readTime;

    /**
     * 抄送时间
     */
    private Date ccTime;
}