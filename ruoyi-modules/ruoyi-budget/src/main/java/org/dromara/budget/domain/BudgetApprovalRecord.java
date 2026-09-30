package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 预算审批记录（每次提交独立一条）
 *
 * <p>解决审批流复用同一条数据只改状态的问题：每次提交生成独立记录，
 * 审批操作针对具体记录，历史记录完整保留。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_approval_record")
public class BudgetApprovalRecord extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 预算方案ID
     */
    private Long planId;

    /**
     * 填报单位ID
     */
    private Long deptId;

    /**
     * 填报单位名称
     */
    private String deptName;

    /**
     * 预算表编号
     */
    private String templateCode;

    /**
     * 预算表名称
     */
    private String templateName;

    /**
     * 提交轮次(1,2,3...)
     */
    private Integer submitRound;

    /**
     * PENDING=待审批 APPROVED=已通过 REJECTED=已驳回
     */
    private String status;

    /**
     * 提交人ID
     */
    private Long submitBy;

    /**
     * 提交人姓名
     */
    private String submitByName;

    /**
     * 提交时间
     */
    private Date submitTime;

    /**
     * 提交说明
     */
    private String submitReason;

    /**
     * 审批人ID
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
     * 科目数
     */
    private Integer itemCount;

    /**
     * 预算合计(万元)
     */
    private BigDecimal totalAmount;
}
