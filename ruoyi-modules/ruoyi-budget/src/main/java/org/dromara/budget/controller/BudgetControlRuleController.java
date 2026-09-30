package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.budget.domain.bo.BudgetControlRuleBo;
import org.dromara.budget.domain.vo.BudgetControlRuleVo;
import org.dromara.budget.service.IBudgetControlRuleService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 预算控制规则(事前拦截)
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/control")
public class BudgetControlRuleController extends BaseController {

    private final IBudgetControlRuleService budgetControlRuleService;

    /**
     * 分页查询预算控制规则
     */
    @SaCheckPermission("budget:control:list")
    @GetMapping("/list")
    public TableDataInfo<BudgetControlRuleVo> list(BudgetControlRuleBo bo, PageQuery pageQuery) {
        return budgetControlRuleService.queryPageList(bo, pageQuery);
    }

    /**
     * 查询预算控制规则列表(不分页)
     */
    @SaCheckPermission("budget:control:list")
    @GetMapping("/queryList")
    public R<List<BudgetControlRuleVo>> queryList(BudgetControlRuleBo bo) {
        return R.ok(budgetControlRuleService.queryList(bo));
    }

    /**
     * 新增预算控制规则
     */
    @SaCheckPermission("budget:control:add")
    @Log(title = "预算控制规则", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody BudgetControlRuleBo bo) {
        return toAjax(budgetControlRuleService.insertByBo(bo));
    }

    /**
     * 修改预算控制规则
     */
    @SaCheckPermission("budget:control:edit")
    @Log(title = "预算控制规则", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody BudgetControlRuleBo bo) {
        return toAjax(budgetControlRuleService.updateByBo(bo));
    }

    /**
     * 启停预算控制规则
     */
    @SaCheckPermission("budget:control:edit")
    @Log(title = "预算控制规则", businessType = BusinessType.UPDATE)
    @PutMapping("/toggle/{id}")
    public R<Void> toggle(@PathVariable @NotNull(message = "规则ID不能为空") Long id,
                          @RequestParam @NotNull(message = "启用状态不能为空") Boolean enabled) {
        return toAjax(budgetControlRuleService.toggleEnabled(id, enabled));
    }

    /**
     * 删除预算控制规则
     */
    @SaCheckPermission("budget:control:remove")
    @Log(title = "预算控制规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(budgetControlRuleService.deleteWithValidByIds(List.of(ids), true));
    }
}