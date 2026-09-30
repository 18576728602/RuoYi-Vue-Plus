package org.dromara.budget.service;

import org.dromara.budget.domain.BudgetApprovalChain;
import org.dromara.budget.domain.BudgetApprovalChainNode;

import java.util.List;
import java.util.Map;

/**
 * 预算多级审批链 Service
 *
 * <p>三级/四级孙公司填报时，从填报单位本级开始自下而上逐级审批。
 * 提供审批链生成、查询、推进、驳回等核心能力。</p>
 */
public interface IBudgetApprovalChainService {

    /**
     * 提交时生成/追加一条审批链。
     * 从填报单位本级(sort_order=1)为起点，沿 ancestors 逐级向上到顶层，每层一个节点。
     * 若无历史进行中的链则开启新轮次(chain_no递增)，否则复用当前进行中的链。
     *
     * @return 本轮审批链主表
     */
    BudgetApprovalChain createChain(Long planId, Long deptId, String templateCode, String submitRemark);

    /**
     * 查询某方案+单位最新一轮审批链及各节点
     */
    Map<String, Object> getLatestChain(Long planId, Long deptId);

    /**
     * 查询当前用户待审批的链节点待办（识别该用户属于哪个公司、可审哪些节点）
     */
    List<Map<String, Object>> listMyPendingChains();

    /**
     * 查询某方案+单位+nodeDeptId 下处于待审的链（供审批中心筛选）
     */
    List<BudgetApprovalChain> listPendingChainsForDept(Long planId, Long nodeDeptId);

    /**
     * 推进审批链：将链上首个 PENDING 节点置为 approval(true)/rejection(false)，
     * 并激活下一节点或终结整链。
     *
     * @param chainNodeId 当前被审批的节点ID
     * @param approve     是否通过
     * @param comment     审批意见
     * @return 整链状态
     */
    String advanceChain(Long chainNodeId, boolean approve, String comment);
}