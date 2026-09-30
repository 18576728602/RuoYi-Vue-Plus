package org.dromara.budget.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.*;
import cn.dev33.satoken.annotation.SaCheckPermission;
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
import org.dromara.budget.domain.vo.DeptBudgetModuleVo;
import org.dromara.budget.domain.bo.DeptBudgetModuleBo;
import org.dromara.budget.service.IDeptBudgetModuleService;
import org.dromara.common.mybatis.core.page.TableDataInfo;

/**
 * 部门-预算板块映射
 *
 * @author Lion Li
 * @date 2026-08-29
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/budgetModule")
public class DeptBudgetModuleController extends BaseController {

    private final IDeptBudgetModuleService deptBudgetModuleService;

    /**
     * 查询部门-预算板块映射列表
     */
    @SaCheckPermission("budget:budgetModule:list")
    @GetMapping("/list")
    public TableDataInfo<DeptBudgetModuleVo> list(DeptBudgetModuleBo bo, PageQuery pageQuery) {
        return deptBudgetModuleService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出部门-预算板块映射列表
     */
    @SaCheckPermission("budget:budgetModule:export")
    @Log(title = "部门-预算板块映射", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(DeptBudgetModuleBo bo, HttpServletResponse response) {
        List<DeptBudgetModuleVo> list = deptBudgetModuleService.queryList(bo);
        ExcelUtil.exportExcel(list, "部门-预算板块映射", DeptBudgetModuleVo.class, response);
    }

    /**
     * 获取部门-预算板块映射详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("budget:budgetModule:query")
    @GetMapping("/{id}")
    public R<DeptBudgetModuleVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(deptBudgetModuleService.queryById(id));
    }

    /**
     * 新增部门-预算板块映射
     */
    @SaCheckPermission("budget:budgetModule:add")
    @Log(title = "部门-预算板块映射", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody DeptBudgetModuleBo bo) {
        return toAjax(deptBudgetModuleService.insertByBo(bo));
    }

    /**
     * 修改部门-预算板块映射
     */
    @SaCheckPermission("budget:budgetModule:edit")
    @Log(title = "部门-预算板块映射", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody DeptBudgetModuleBo bo) {
        return toAjax(deptBudgetModuleService.updateByBo(bo));
    }

    /**
     * 删除部门-预算板块映射
     *
     * @param ids 主键串
     */
    @SaCheckPermission("budget:budgetModule:remove")
    @Log(title = "部门-预算板块映射", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(deptBudgetModuleService.deleteWithValidByIds(List.of(ids), true));
    }
}
