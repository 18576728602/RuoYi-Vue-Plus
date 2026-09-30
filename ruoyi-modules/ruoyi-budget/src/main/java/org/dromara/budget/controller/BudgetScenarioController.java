package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import lombok.RequiredArgsConstructor;
import org.dromara.budget.domain.bo.BudgetScenarioBo;
import org.dromara.budget.domain.vo.BudgetScenarioResultVo;
import org.dromara.budget.domain.vo.BudgetScenarioVo;
import org.dromara.budget.domain.vo.BudgetSensitivityRow;
import org.dromara.budget.service.IBudgetScenarioService;
import org.dromara.common.core.domain.R;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 情景模拟与敏感性分析
 *
 * @author Lion Li
 * @date 2026-09-15
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/scenario")
public class BudgetScenarioController {

    private final IBudgetScenarioService budgetScenarioService;

    /**
     * 情景参数列表
     */
    @SaCheckPermission("budget:scenario:list")
    @GetMapping("/list")
    public R<List<BudgetScenarioVo>> list(@RequestParam Long planId) {
        return R.ok(budgetScenarioService.listScenarios(planId));
    }

    /**
     * 保存情景参数（新增/更新）
     */
    @SaCheckPermission("budget:scenario:save")
    @PostMapping("/save")
    public R<Void> save(@RequestBody @Validated BudgetScenarioBo bo) {
        budgetScenarioService.saveScenario(bo);
        return R.ok();
    }

    /**
     * 删除情景
     */
    @SaCheckPermission("budget:scenario:remove")
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        budgetScenarioService.deleteScenario(id);
        return R.ok();
    }

    /**
     * 情景模拟结果（多情景对比：指标表 + 雷达图 + 瀑布图）
     */
    @SaCheckPermission(value = {"budget:scenario:list", "budget:scenario:simulate"}, mode = SaMode.OR)
    @GetMapping("/simulate")
    public R<BudgetScenarioResultVo> simulate(@RequestParam Long planId, @RequestParam(required = false) Long orgId) {
        return R.ok(budgetScenarioService.simulate(planId, orgId));
    }

    /**
     * 敏感性分析（龙卷风图数据）
     */
    @SaCheckPermission(value = {"budget:scenario:list", "budget:scenario:sensitivity"}, mode = SaMode.OR)
    @GetMapping("/sensitivity")
    public R<List<BudgetSensitivityRow>> sensitivity(@RequestParam Long planId, @RequestParam(required = false) Long orgId) {
        return R.ok(budgetScenarioService.sensitivity(planId, orgId));
    }
}