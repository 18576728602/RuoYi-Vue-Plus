package org.dromara.budget.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;

import java.util.List;
import java.util.Map;

/**
 * 审批中心 Service
 */
public interface IBudgetApprovalService {

    /**
     * 查询审批列表（status: PENDING=待审批, APPROVED=已通过, REJECTED=已驳回）
     */
    TableDataInfo<Map<String, Object>> queryPendingList(String type, Long planId, Long orgId, String status, PageQuery pageQuery);

    /**
     * 统计概览
     */
    Map<String, Object> getStats(Long planId, String type, Long orgId);

    /**
     * 批量审批
     */
    void batchApprove(List<Long> ids, String type, String action, String remark);

    /**
     * 审批轨迹（PRD 8.6）：基于操作日志 + 转交记录，按时间线聚合
     */
    List<Map<String, Object>> getTrace(String type, Long planId, Long deptId, String templateCode);

    /**
     * 审批转交（PRD 8.4）：将待办转给其他审批人，全程留痕
     */
    void transfer(Long planId, Long deptId, String templateCode, String type, Long receiverId, String reason);

    /**
     * 审批抄送（会签知情）：将审批单抄送多人知悉，被抄送人不参与审批
     */
    void cc(Long planId, Long deptId, String templateCode, String type, List<Long> receiverIds, String reason);

    /**
     * 我的抄送（被抄送人视角）：查询抄送给当前用户的审批单
     */
    TableDataInfo<Map<String, Object>> queryMyCcList(String type, PageQuery pageQuery);

    /**
     * 标记抄送已读
     */
    void markCcRead(Long ccId);

    /**
     * 审批效率统计（PRD 8.5）：各类型平均审批时长 + 当前待办滞留时长
     */
    Map<String, Object> getEfficiency(Long planId, String type, Long orgId);

    /**
     * 查询可抄送/转交的候选用户（拥有预算审批相关权限的用户）
     * @param excludeUserId 需排除的用户ID（如申请人）
     */
    List<Map<String, Object>> listCcCandidates(Long excludeUserId);
}
