package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Date;

/**
 * 审批转交记录对象 budget_approval_transfer
 *
 * <p>PRD 8.4 审批转交：审批人将单个待办任务转交给其他审批人办理，
 * 需填写转交原因，转交记录全程留痕，可用于审批轨迹追溯。</p>
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_approval_transfer")
public class BudgetApprovalTransfer extends TenantEntity {

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
     * 转交人ID
     */
    private Long transfererId;

    /**
     * 转交人名称
     */
    private String transfererName;

    /**
     * 接收人ID
     */
    private Long receiverId;

    /**
     * 接收人名称
     */
    private String receiverName;

    /**
     * 转交原因
     */
    private String reason;

    /**
     * 转交时间
     */
    private Date transferTime;
}