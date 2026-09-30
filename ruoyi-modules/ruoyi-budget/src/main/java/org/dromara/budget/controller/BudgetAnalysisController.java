package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.budget.domain.bo.BudgetAnalysisQuery;
import org.dromara.budget.domain.vo.BudgetAnalysisOverview;
import org.dromara.budget.domain.vo.BudgetAnalysisResult;
import org.dromara.budget.service.IBudgetAnalysisService;
import org.dromara.common.core.domain.R;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预算执行差异分析
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/analysis")
public class BudgetAnalysisController {

    private final IBudgetAnalysisService budgetAnalysisService;

    /**
     * 差异概览卡
     */
    @SaCheckPermission("budget:analysis:list")
    @GetMapping("/overview")
    public R<BudgetAnalysisOverview> overview(@Validated BudgetAnalysisQuery q) {
        return R.ok(budgetAnalysisService.overview(q));
    }

    /**
     * 差异归因列表（单位/科目维度）
     */
    @SaCheckPermission("budget:analysis:list")
    @GetMapping("/analyze")
    public R<BudgetAnalysisResult> analyze(@Validated BudgetAnalysisQuery q) {
        return R.ok(budgetAnalysisService.analyze(q));
    }
}