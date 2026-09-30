package org.dromara.budget.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.*;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.web.core.BaseController;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.budget.domain.vo.BudgetPlanVo;
import org.dromara.budget.domain.bo.BudgetPlanBo;
import org.dromara.budget.service.IBudgetPlanService;
import org.dromara.common.mybatis.core.page.TableDataInfo;

/**
 * 预算方案
 *
 * @author Lion Li
 * @date 2026-08-29
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/plan")
public class BudgetPlanController extends BaseController {

    private final IBudgetPlanService budgetPlanService;

    /**
     * 查询预算方案列表
     */
    @SaCheckPermission(value = {"budget:plan:list", "budget:plan:query"}, mode = SaMode.OR)
    @GetMapping("/list")
    public TableDataInfo<BudgetPlanVo> list(BudgetPlanBo bo, PageQuery pageQuery) {
        return budgetPlanService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出预算方案列表
     */
    @SaCheckPermission("budget:plan:export")
    @Log(title = "预算方案", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BudgetPlanBo bo, HttpServletResponse response) {
        List<BudgetPlanVo> list = budgetPlanService.queryList(bo);
        ExcelUtil.exportExcel(list, "预算方案", BudgetPlanVo.class, response);
    }

    /**
     * 获取预算方案详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("budget:plan:query")
    @GetMapping("/{id}")
    public R<BudgetPlanVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(budgetPlanService.queryById(id));
    }

    /**
     * 新增预算方案
     */
    @SaCheckPermission("budget:plan:add")
    @Log(title = "预算方案", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Long> add(@Validated(AddGroup.class) @RequestBody BudgetPlanBo bo) {
        return R.ok(budgetPlanService.insertByBo(bo));
    }

    /**
     * 修改预算方案
     */
    @SaCheckPermission("budget:plan:edit")
    @Log(title = "预算方案", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody BudgetPlanBo bo) {
        return toAjax(budgetPlanService.updateByBo(bo));
    }

    /**
     * 删除预算方案
     *
     * @param ids 主键串
     */
    @SaCheckPermission("budget:plan:remove")
    @Log(title = "预算方案", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(budgetPlanService.deleteWithValidByIds(List.of(ids), true));
    }

    /**
     * 修改方案状态（发布/归档/关闭）
     */
    @SaCheckPermission("budget:plan:edit")
    @Log(title = "预算方案", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestBody BudgetPlanBo bo) {
        budgetPlanService.changeStatus(bo);
        return R.ok();
    }
}
