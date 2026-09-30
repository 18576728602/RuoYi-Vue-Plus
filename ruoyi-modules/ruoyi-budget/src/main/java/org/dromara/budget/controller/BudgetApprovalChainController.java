package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import lombok.RequiredArgsConstructor;
import org.dromara.budget.service.IBudgetApprovalChainService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 预算多级审批链
 *
 * <p>三级/四级孙公司填报后，从本级开始自下而上逐级审批。</p>
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/approvalChain")
public class BudgetApprovalChainController {

    private final IBudgetApprovalChainService approvalChainService;

    /**
     * 当前用户待审批的链待办（按所属公司层级自动识别）
     */
    @SaCheckPermission(value = {"budget:approval:list", "budget:approval:approve"}, mode = SaMode.OR)
    @GetMapping("/myPending")
    public R<List<Map<String, Object>>> myPending() {
        return R.ok(approvalChainService.listMyPendingChains());
    }

    /**
     * 查询某方案+单位最新一轮审批链及各节点（审批轨迹/进度）
     */
    @SaCheckPermission("budget:approval:list")
    @GetMapping("/latest")
    public R<Map<String, Object>> latest(@RequestParam Long planId,
                                         @RequestParam Long deptId) {
        return R.ok(approvalChainService.getLatestChain(planId, deptId));
    }

    /**
     * 推进审批链：对指定节点通过/驳回
     */
    @SaCheckPermission("budget:approval:approve")
    @PostMapping("/advance")
    public R<String> advance(@RequestBody Map<String, Object> body) {
        Long nodeId = body.get("nodeId") == null ? null : Long.valueOf(String.valueOf(body.get("nodeId")));
        Boolean approve = body.get("approve") == null ? Boolean.TRUE : Boolean.valueOf(String.valueOf(body.get("approve")));
        String comment = (String) body.get("comment");
        return R.ok(approvalChainService.advanceChain(nodeId, approve, comment));
    }
}