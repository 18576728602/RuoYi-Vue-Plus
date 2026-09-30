package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.service.IBudgetApprovalService;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 审批中心
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/approval")
public class BudgetApprovalController {

    private final IBudgetApprovalService approvalService;

    /**
     * 查询待审批列表
     */
    @SaCheckPermission("budget:approval:list")
    @GetMapping("/list")
    public TableDataInfo<Map<String, Object>> list(
        @RequestParam(required = false) String type,
        @RequestParam(required = false) Long planId,
        @RequestParam(required = false) Long orgId,
        @RequestParam(required = false) String status,
        PageQuery pageQuery
    ) {
        return approvalService.queryPendingList(type, planId, orgId, status, pageQuery);
    }

    /**
     * 统计概览
     */
    @SaCheckPermission("budget:approval:list")
    @GetMapping("/stats")
    public R<Map<String, Object>> stats(
        @RequestParam(required = false) Long planId,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) Long orgId
    ) {
        return R.ok(approvalService.getStats(planId, type, orgId));
    }

    /**
     * 批量审批（预算填报审批 或 预算调整审批，任一权限即可调用；类型权限另行在Service内校验）
     */
    @SaCheckPermission(value = {"budget:approval:approve", "budget:adjustment:approve"}, mode = SaMode.OR)
    @PostMapping("/batch")
    public R<Void> batchApprove(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) body.get("ids");
        String type = (String) body.get("type");
        String action = (String) body.get("action");
        String remark = (String) body.get("remark");
        approvalService.batchApprove(ids, type, action, remark);
        return R.ok();
    }

    /**
     * 审批轨迹（PRD 8.6）：时间线视图
     */
    @SaCheckPermission("budget:approval:list")
    @GetMapping("/trace")
    public R<List<Map<String, Object>>> trace(
        @RequestParam(required = false) String type,
        @RequestParam(required = false) Long planId,
        @RequestParam(required = false) Long deptId,
        @RequestParam(required = false) String templateCode
    ) {
        return R.ok(approvalService.getTrace(type, planId, deptId, templateCode));
    }

    /**
     * 审批转交（PRD 8.4）：全程留痕
     */
    @SaCheckPermission(value = {"budget:approval:approve", "budget:adjustment:approve"}, mode = SaMode.OR)
    @PostMapping("/transfer")
    public R<Void> transfer(@RequestBody Map<String, Object> body) {
        Long planId = body.get("planId") == null ? null : Long.valueOf(String.valueOf(body.get("planId")));
        Long deptId = body.get("deptId") == null ? null : Long.valueOf(String.valueOf(body.get("deptId")));
        String templateCode = (String) body.get("templateCode");
        String type = (String) body.get("type");
        Long receiverId = body.get("receiverId") == null ? null : Long.valueOf(String.valueOf(body.get("receiverId")));
        String reason = (String) body.get("reason");
        approvalService.transfer(planId, deptId, templateCode, type, receiverId, reason);
        return R.ok();
    }

    /**
     * 审批抄送（会签知情）：抄送多人知悉，全程留痕
     */
    @SaCheckPermission(value = {"budget:approval:approve", "budget:adjustment:approve"}, mode = SaMode.OR)
    @PostMapping("/copy")
    public R<Void> copy(@RequestBody Map<String, Object> body) {
        Long planId = body.get("planId") == null ? null : Long.valueOf(String.valueOf(body.get("planId")));
        Long deptId = body.get("deptId") == null ? null : Long.valueOf(String.valueOf(body.get("deptId")));
        String templateCode = (String) body.get("templateCode");
        String type = (String) body.get("type");
        @SuppressWarnings("unchecked")
        List<Object> receiverIdsRaw = (List<Object>) body.get("receiverIds");
        List<Long> receiverIds = new ArrayList<>();
        if (receiverIdsRaw != null) {
            for (Object o : receiverIdsRaw) {
                if (o != null) {
                    receiverIds.add(Long.valueOf(String.valueOf(o)));
                }
            }
        }
        String reason = (String) body.get("reason");
        approvalService.cc(planId, deptId, templateCode, type, receiverIds, reason);
        return R.ok();
    }

    /**
     * 查询可抄送/转交的候选用户（仅拥有预算审批权限的用户）
     */
    @SaCheckPermission(value = {"budget:approval:approve", "budget:adjustment:approve"}, mode = SaMode.OR)
    @GetMapping("/ccCandidates")
    public R<List<Map<String, Object>>> ccCandidates(@RequestParam(required = false) Long excludeUserId) {
        return R.ok(approvalService.listCcCandidates(excludeUserId));
    }

    /**
     * 我的抄送（被抄送人视角）
     */
    @GetMapping("/myCopy")
    public TableDataInfo<Map<String, Object>> myCopy(
        @RequestParam(required = false) String type,
        PageQuery pageQuery
    ) {
        return approvalService.queryMyCcList(type, pageQuery);
    }

    /**
     * 标记抄送已读
     */
    @PostMapping("/copyRead/{ccId}")
    public R<Void> copyRead(@PathVariable Long ccId) {
        approvalService.markCcRead(ccId);
        return R.ok();
    }

    /**
     * 审批效率统计（PRD 8.5）：平均审批时长 + 待办滞留
     */
    @SaCheckPermission("budget:approval:list")
    @GetMapping("/efficiency")
    public R<Map<String, Object>> efficiency(
        @RequestParam(required = false) Long planId,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) Long orgId
    ) {
        return R.ok(approvalService.getEfficiency(planId, type, orgId));
    }
}
