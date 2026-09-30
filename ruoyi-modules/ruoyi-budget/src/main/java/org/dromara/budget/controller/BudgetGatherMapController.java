package org.dromara.budget.controller;

import java.util.List;
import java.util.Map;

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
import org.dromara.budget.domain.vo.BudgetGatherMapVo;
import org.dromara.budget.domain.bo.BudgetGatherMapBo;
import org.dromara.budget.service.IBudgetGatherMapService;
import org.dromara.common.mybatis.core.page.TableDataInfo;

/**
 * 预算科目归集映射
 *
 * @author Lion Li
 * @date 2026-09-16
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/gather")
public class BudgetGatherMapController extends BaseController {

    private final IBudgetGatherMapService budgetGatherMapService;

    /**
     * 查询本部内设部门（归集源部门下拉数据源）
     */
    @SaCheckPermission("budget:gather:list")
    @GetMapping("/depts")
    public R<List<Map<String, Object>>> depts() {
        return R.ok(budgetGatherMapService.listInnerDepts());
    }

    /**
     * 查询预算科目归集映射列表
     */
    @SaCheckPermission("budget:gather:list")
    @GetMapping("/list")
    public TableDataInfo<BudgetGatherMapVo> list(BudgetGatherMapBo bo, PageQuery pageQuery) {
        return budgetGatherMapService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出预算科目归集映射列表
     */
    @SaCheckPermission("budget:gather:export")
    @Log(title = "预算科目归集映射", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BudgetGatherMapBo bo, HttpServletResponse response) {
        List<BudgetGatherMapVo> list = budgetGatherMapService.queryList(bo);
        ExcelUtil.exportExcel(list, "预算科目归集映射", BudgetGatherMapVo.class, response);
    }

    /**
     * 获取预算科目归集映射详细信息
     */
    @SaCheckPermission("budget:gather:query")
    @GetMapping("/{id}")
    public R<BudgetGatherMapVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(budgetGatherMapService.queryById(id));
    }

    /**
     * 新增预算科目归集映射
     */
    @SaCheckPermission("budget:gather:add")
    @Log(title = "预算科目归集映射", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody BudgetGatherMapBo bo) {
        return toAjax(budgetGatherMapService.insertByBo(bo));
    }

    /**
     * 修改预算科目归集映射
     */
    @SaCheckPermission("budget:gather:edit")
    @Log(title = "预算科目归集映射", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody BudgetGatherMapBo bo) {
        return toAjax(budgetGatherMapService.updateByBo(bo));
    }

    /**
     * 删除预算科目归集映射
     */
    @SaCheckPermission("budget:gather:remove")
    @Log(title = "预算科目归集映射", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(budgetGatherMapService.deleteWithValidByIds(List.of(ids), true));
    }

    /**
     * 执行归集：把指定方案下本部内设部门17表明细，归集汇总到本部06表目标科目
     */
    @SaCheckPermission("budget:gather:gather")
    @Log(title = "预算科目归集映射", businessType = BusinessType.UPDATE)
    @PostMapping("/run")
    public R<Integer> gather(@NotNull(message = "方案ID不能为空") @RequestParam Long planId) {
        return R.ok(budgetGatherMapService.gather(planId));
    }
}