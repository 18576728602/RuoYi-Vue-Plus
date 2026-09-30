package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Date;

/**
 * 预算审批链主表 budget_approval_chain
 *
 * <p>多级逐级审批：三级/四级孙公司填报时，从填报单位本级开始自下而上逐级审批。
 * 一次提交(同一方案+同一填报单位)生成一条链，chain_no 区分多轮提交。</p>
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_approval_chain")
public class BudgetApprovalChain extends TenantEntity {

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
     * 填报单位(公司)ID
     */
    private Long deptId;

    /**
     * 预算表编号(按表分批审批, 对应 budget_data.template_code)
     */
    private String templateCode;

    /**
     * 轮次(同一方案+单位+表多轮提交递增:1,2,3...)
     */
    private Integer chainNo;

    /**
     * 整链状态: PENDING进行中 / APPROVED全部通过 / REJECTED被驳回
     */
    private String status;

    /**
     * 本轮发起时间
     */
    private Date startTime;

    /**
     * 发起人ID
     */
    private Long applicantId;

    /**
     * 发起人名称
     */
    private String applicantName;

    /**
     * 发起说明
     */
    private String remark;
}