package org.dromara.budget.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.budget.service.IBudgetDashboardService;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 预算工作台仪表盘
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/dashboard")
public class BudgetDashboardController extends BaseController {

    private final IBudgetDashboardService budgetDashboardService;

    /**
     * 获取统计卡片数据
     */
    @GetMapping("/stats")
    public R<Map<String, Object>> getStats(@RequestParam(required = false) Long planId,
                                           @RequestParam(required = false) Long orgId) {
        return R.ok(budgetDashboardService.getStats(planId, orgId));
    }

    /**
     * 获取各单位填报进度
     */
    @GetMapping("/fillProgress")
    public R<List<Map<String, Object>>> getFillProgress(@RequestParam(required = false) Long planId,
                                                        @RequestParam(required = false) Long orgId) {
        return R.ok(budgetDashboardService.getFillProgress(planId, orgId));
    }

    /**
     * 获取待办事项
     */
    @GetMapping("/todoList")
    public R<List<Map<String, Object>>> getTodoList() {
        return R.ok(budgetDashboardService.getTodoList());
    }

    /**
     * 获取最新动态
     */
    @GetMapping("/recentActivities")
    public R<List<Map<String, Object>>> getRecentActivities(@RequestParam(required = false) Long planId,
                                                            @RequestParam(required = false) Long orgId) {
        return R.ok(budgetDashboardService.getRecentActivities(planId, orgId));
    }

    /**
     * 获取季度执行趋势
     */
    @GetMapping("/quarterTrend")
    public R<Map<String, Object>> getQuarterTrend(@RequestParam(required = false) Long planId,
                                                  @RequestParam(required = false) Long orgId) {
        return R.ok(budgetDashboardService.getQuarterTrend(planId, orgId));
    }

    /**
     * 获取可选择填报单位列表
     */
    @GetMapping("/unitOptions")
    public R<List<Map<String, Object>>> getUnitOptions() {
        return R.ok(budgetDashboardService.getUnitOptions());
    }
}
