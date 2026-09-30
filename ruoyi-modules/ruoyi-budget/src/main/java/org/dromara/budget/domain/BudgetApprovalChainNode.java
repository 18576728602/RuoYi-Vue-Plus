package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Date;

/**
 * 预算审批链节点表 budget_approval_chain_node
 *
 * <p>每条审批链的各级审批节点：sort_order=1 为最低层(填报单位本级先审)，逐级向上递增。</p>
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_approval_chain_node")
public class BudgetApprovalChainNode extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 审批链主表ID
     */
    private Long chainId;

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
     * 轮次(同链主表)
     */
    private Integer chainNo;

    /**
     * 审批顺序(1=最低层本级先审,向上递增)
     */
    private Integer sortOrder;

    /**
     * 该层公司(审批单位)ID
     */
    private Long nodeDeptId;

    /**
     * 该层公司名称
     */
    private String nodeDeptName;

    /**
     * 层级深度(1=填报单位本级,2=上级,依次向上)
     */
    private Integer levelNo;

    /**
     * 节点状态: PENDING待审 / APPROVED已通过 / REJECTED已驳回 / WAITING排队 / SKIPPED跳过(无审批人)
     */
    private String status;

    /**
     * 审批人ID
     */
    private Long approverId;

    /**
     * 审批人名称
     */
    private String approverName;

    /**
     * 审批时间
     */
    private Date approveTime;

    /**
     * 审批意见
     */
    private String comment;

    /**
     * 节点类型: DEPT=公司层级节点 / POST=岗位审批节点
     */
    private String nodeType;

    /**
     * 岗位节点权限标识（nodeType=POST 时有值，如 budget:approval:minister）
     */
    private String postPerm;
}