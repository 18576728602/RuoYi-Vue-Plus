package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.budget.domain.bo.BudgetTargetBo;
import org.dromara.budget.service.IBudgetTargetService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 预算目标下达 Controller（一下）
 *
 * <p>PRD 7.1.2 第一阶段：集团下达预算目标基准线；子公司只读查看本公司目标。</p>
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/target")
public class BudgetTargetController {

    private final IBudgetTargetService targetService;

    /**
     * 目标下达公司列表（集团）
     */
    @SaCheckPermission("budget:target:list")
    @GetMapping("/companies")
    public R<List<Map<String, Object>>> companies(@NotNull(message = "方案不能为空") Long planId) {
        return R.ok(targetService.listCompanyTargets(planId));
    }

    /**
     * 目标明细 / 版本（集团查看；versionNo 可选）
     */
    @SaCheckPermission("budget:target:list")
    @GetMapping("/detail")
    public R<List<Map<String, Object>>> detail(@NotNull(message = "方案不能为空") Long planId,
                                               @NotNull(message = "公司不能为空") Long deptId,
                                               @RequestParam(required = false) String templateCode,
                                               @RequestParam(required = false) Integer versionNo) {
        return R.ok(targetService.getDetail(planId, deptId, templateCode, versionNo));
    }

    /**
     * 目标编辑科目集（集团）
     */
    @SaCheckPermission("budget:target:edit")
    @GetMapping("/buildItems")
    public R<List<Map<String, Object>>> buildItems(@NotNull(message = "方案不能为空") Long planId,
                                                   @NotNull(message = "公司不能为空") Long deptId,
                                                   @RequestParam(required = false) String templateCode) {
        return R.ok(targetService.buildItems(planId, deptId, templateCode));
    }

    /**
     * 保存目标草稿（集团）
     */
    @SaCheckPermission("budget:target:edit")
    @PostMapping("/draft")
    public R<Void> saveDraft(@Valid @RequestBody BudgetTargetBo bo) {
        targetService.saveDraft(bo);
        return R.ok();
    }

    /**
     * 下达目标（草稿→已下达）
     */
    @SaCheckPermission("budget:target:publish")
    @PutMapping("/publish")
    public R<Void> publish(@NotNull(message = "方案不能为空") Long planId,
                           @NotNull(message = "公司不能为空") Long deptId) {
        targetService.publishTarget(planId, deptId);
        return R.ok();
    }

    /**
     * 目标版本历史（集团）
     */
    @SaCheckPermission("budget:target:list")
    @GetMapping("/history")
    public R<List<Map<String, Object>>> history(@NotNull(message = "方案不能为空") Long planId,
                                                @NotNull(message = "公司不能为空") Long deptId) {
        return R.ok(targetService.listHistory(planId, deptId));
    }

    /**
     * 子公司查看本公司目标（已下达，只读）
     */
    @SaCheckPermission(value = {"budget:target:list", "budget:fill:list"}, mode = SaMode.OR)
    @GetMapping("/my")
    public R<List<Map<String, Object>>> myTargets(@NotNull(message = "方案不能为空") Long planId,
                                                  @NotNull(message = "公司不能为空") Long deptId,
                                                  @RequestParam(required = false) String templateCode) {
        return R.ok(targetService.getMyTargets(planId, deptId, templateCode));
    }

    /**
     * 目标 vs 实际填报 对比（二下：差异率/超区间高亮）
     */
    @SaCheckPermission(value = {"budget:target:list", "budget:approval:list", "budget:fill:list"}, mode = SaMode.OR)
    @GetMapping("/compareFill")
    public R<Map<String, Object>> compareFill(@NotNull(message = "方案不能为空") Long planId,
                                              @NotNull(message = "公司不能为空") Long deptId,
                                              @RequestParam(required = false) String templateCode) {
        return R.ok(targetService.compareFill(planId, deptId, templateCode));
    }
}