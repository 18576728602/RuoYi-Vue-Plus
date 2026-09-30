package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.bo.BudgetApprovalCommentBo;
import org.dromara.budget.domain.vo.BudgetApprovalCommentVo;
import org.dromara.budget.service.IBudgetApprovalCommentService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审批批注（PRD 8.2 单元格级批注）
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/approvalComment")
public class BudgetApprovalCommentController {

    private final IBudgetApprovalCommentService commentService;

    /**
     * 新增批注（审批人/填报人）
     */
    @SaCheckPermission("budget:approval:list")
    @PostMapping
    public R<BudgetApprovalCommentVo> add(@Valid @RequestBody BudgetApprovalCommentBo bo) {
        return R.ok(commentService.addComment(bo));
    }

    /**
     * 查询批注列表（方案+单位+预算表 范围，可按科目和审批记录过滤）
     */
    @SaCheckPermission(value = {"budget:approval:list", "budget:fill:list"}, mode = SaMode.OR)
    @GetMapping("/list")
    public R<List<BudgetApprovalCommentVo>> list(
        @RequestParam(required = false) String targetType,
        @RequestParam(required = false) Long planId,
        @RequestParam(required = false) Long deptId,
        @RequestParam(required = false) String templateCode,
        @RequestParam(required = false) String itemCode,
        @RequestParam(required = false) Long recordId
    ) {
        return R.ok(commentService.listByScopeWithRecord(targetType, planId, deptId, templateCode, itemCode, recordId));
    }

    /**
     * 回复批注（填报人）
     */
    @SaCheckPermission(value = {"budget:approval:list", "budget:fill:list"}, mode = SaMode.OR)
    @PostMapping("/{id}/reply")
    public R<BudgetApprovalCommentVo> reply(@PathVariable Long id,
                                            @RequestParam(required = false) String replyContent,
                                            @RequestParam(required = false) String status) {
        return R.ok(commentService.reply(id, replyContent, status));
    }

    /**
     * 标记批注为已处理/已采纳
     */
    @SaCheckPermission("budget:approval:list")
    @PutMapping("/{id}/handle")
    public R<BudgetApprovalCommentVo> handle(@PathVariable Long id, @RequestParam(required = false) String status) {
        return R.ok(commentService.handle(id, status));
    }

    /**
     * 删除批注
     */
    @SaCheckPermission("budget:approval:list")
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        commentService.remove(id);
        return R.ok();
    }
}