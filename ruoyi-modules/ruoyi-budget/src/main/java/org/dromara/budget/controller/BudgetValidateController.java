package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import lombok.RequiredArgsConstructor;
import org.dromara.budget.domain.bo.BudgetValidateQuery;
import org.dromara.budget.domain.vo.BudgetValidateResult;
import org.dromara.budget.service.IBudgetValidateService;
import org.dromara.common.core.domain.R;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 填报校验
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/validate")
public class BudgetValidateController {

    private final IBudgetValidateService budgetValidateService;

    /**
     * 执行填报校验
     */
    @SaCheckPermission(value = {"budget:validate:list", "budget:fill:list"}, mode = SaMode.OR)
    @GetMapping
    public R<BudgetValidateResult> validate(@Validated BudgetValidateQuery q) {
        return R.ok(budgetValidateService.validate(q));
    }
}