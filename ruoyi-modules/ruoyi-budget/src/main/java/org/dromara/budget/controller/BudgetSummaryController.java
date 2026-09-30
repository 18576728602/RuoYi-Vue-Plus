package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.budget.domain.vo.BudgetSummaryVo;
import org.dromara.budget.service.IBudgetSummaryService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 预算汇总看板
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/budget/summary")
public class BudgetSummaryController {

    private final IBudgetSummaryService budgetSummaryService;

    /**
     * 获取集团总览
     */
    @SaCheckPermission("budget:summary:list")
    @GetMapping("/overview")
    public R<BudgetSummaryVo.Overview> getOverview(@RequestParam Long planId) {
        return R.ok(budgetSummaryService.getOverview(planId));
    }

    /**
     * 获取各单位填报汇总
     */
    @SaCheckPermission("budget:summary:list")
    @GetMapping("/deptSummary")
    public R<List<BudgetSummaryVo.DeptSummary>> getDeptSummaryList(@RequestParam Long planId) {
        return R.ok(budgetSummaryService.getDeptSummaryList(planId));
    }

    /**
     * 获取各板块汇总
     */
    @SaCheckPermission("budget:summary:list")
    @GetMapping("/templateSummary")
    public R<List<BudgetSummaryVo.TemplateSummary>> getTemplateSummaryList(@RequestParam Long planId) {
        return R.ok(budgetSummaryService.getTemplateSummaryList(planId));
    }
}
