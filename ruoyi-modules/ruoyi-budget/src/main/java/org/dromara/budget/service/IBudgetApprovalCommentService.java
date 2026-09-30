package org.dromara.budget.service;

import org.dromara.budget.domain.bo.BudgetApprovalCommentBo;
import org.dromara.budget.domain.vo.BudgetApprovalCommentVo;

import java.util.List;

/**
 * 审批批注 Service
 *
 * <p>PRD 8.2 单元格级批注：审批人添加意见，填报人逐条回复，全程留痕。</p>
 */
public interface IBudgetApprovalCommentService {

    /**
     * 新增批注
     */
    BudgetApprovalCommentVo addComment(BudgetApprovalCommentBo bo);

    /**
     * 查询某填报\调整范围（方案+单位+预算表）下的批注列表
     */
    List<BudgetApprovalCommentVo> listByScope(String targetType, Long planId, Long deptId, String templateCode, String itemCode);

    /**
     * 查询批注列表（可按审批记录ID过滤，实现按轮次隔离）
     */
    List<BudgetApprovalCommentVo> listByScopeWithRecord(String targetType, Long planId, Long deptId, String templateCode, String itemCode, Long recordId);

    /**
     * 填报人回复批注
     */
    BudgetApprovalCommentVo reply(Long id, String replyContent, String status);

    /**
     * 将批注标记为已处理
     */
    BudgetApprovalCommentVo handle(Long id, String status);

    /**
     * 删除批注
     */
    void remove(Long id);
}