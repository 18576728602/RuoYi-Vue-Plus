package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.budget.domain.bo.BudgetValidateRuleBo;
import org.dromara.budget.domain.vo.BudgetValidateRuleVo;
import org.dromara.budget.service.IBudgetValidateRuleService;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 填报校验规则配置
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/validateRule")
public class BudgetValidateRuleController extends BaseController {

    private final IBudgetValidateRuleService budgetValidateRuleService;

    @SaCheckPermission("budget:validate:query")
    @GetMapping("/list")
    public R<List<BudgetValidateRuleVo>> list() {
        return R.ok(budgetValidateRuleService.list());
    }

    @SaCheckPermission("budget:validate:query")
    @GetMapping("/{id}")
    public R<BudgetValidateRuleVo> getInfo(@PathVariable Long id) {
        return R.ok(budgetValidateRuleService.getById(id));
    }

    @SaCheckPermission("budget:validate:list")
    @Log(title = "校验规则配置", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BudgetValidateRuleBo bo) {
        budgetValidateRuleService.add(bo);
        return R.ok();
    }

    @SaCheckPermission("budget:validate:list")
    @Log(title = "校验规则配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> update(@Validated @RequestBody BudgetValidateRuleBo bo) {
        budgetValidateRuleService.update(bo);
        return R.ok();
    }

    @SaCheckPermission("budget:validate:list")
    @Log(title = "校验规则配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        budgetValidateRuleService.delete(id);
        return R.ok();
    }

    @SaCheckPermission("budget:validate:list")
    @PutMapping("/toggleEnabled")
    public R<Void> toggleEnabled(@RequestParam Long id, @RequestParam Long enabled) {
        budgetValidateRuleService.toggleEnabled(id, enabled);
        return R.ok();
    }
}
