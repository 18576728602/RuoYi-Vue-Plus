package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.budget.domain.bo.BudgetForecastQuery;
import org.dromara.budget.domain.vo.BudgetForecastDetailVo;
import org.dromara.budget.domain.vo.BudgetForecastVersionVo;
import org.dromara.budget.service.IBudgetForecastService;
import org.dromara.common.core.domain.R;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 滚动预测
 *
 * @author Lion Li
 * @date 2026-09-15
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/forecast")
public class BudgetForecastController {

    private final IBudgetForecastService budgetForecastService;

    /**
     * 生成/重新生成一次滚动预测（按刷新时点截取实际，剩余季度推算）
     */
    @SaCheckPermission("budget:forecast:generate")
    @PostMapping("/generate")
    public R<Long> generate(@RequestBody @Validated BudgetForecastQuery query) {
        return R.ok(budgetForecastService.generateForecast(query));
    }

    /**
     * 预测版本列表（按方案+单位）
     */
    @SaCheckPermission(value = {"budget:forecast:list", "budget:forecast:generate"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    @GetMapping("/list")
    public R<List<BudgetForecastVersionVo>> list(@RequestParam Long planId, @RequestParam(required = false) Long orgId) {
        return R.ok(budgetForecastService.listVersions(planId, orgId));
    }

    /**
     * 预测版本详情（明细行 + 趋势图）
     */
    @SaCheckPermission("budget:forecast:list")
    @GetMapping("/detail/{forecastId}")
    public R<BudgetForecastDetailVo> detail(@PathVariable Long forecastId) {
        return R.ok(budgetForecastService.getForecastDetail(forecastId));
    }

    /**
     * 删除预测版本
     */
    @SaCheckPermission("budget:forecast:remove")
    @DeleteMapping("/{forecastId}")
    public R<Void> remove(@PathVariable Long forecastId) {
        budgetForecastService.deleteForecast(forecastId);
        return R.ok();
    }
}