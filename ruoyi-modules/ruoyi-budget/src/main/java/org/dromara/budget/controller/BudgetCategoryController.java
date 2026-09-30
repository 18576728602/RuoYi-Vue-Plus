package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.budget.domain.bo.BudgetCategoryBo;
import org.dromara.budget.domain.vo.BudgetCategoryVo;
import org.dromara.budget.service.IBudgetCategoryService;
import org.dromara.common.core.domain.R;
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
 * 预算业务分类
 *
 * <p>科目明细页的配套分类字典(独立于雪花ID,用分类编码关联科目)。分类下平铺明细。</p>
 *
 * @author Lion Li
 * @date 2026-09-28
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/category")
public class BudgetCategoryController extends BaseController {

    private final IBudgetCategoryService categoryService;

    /**
     * 分页查询业务分类
     */
    @SaCheckPermission("budget:subject:list")
    @GetMapping("/page")
    public TableDataInfo<BudgetCategoryVo> page(BudgetCategoryBo bo, PageQuery pageQuery) {
        return categoryService.queryPageList(bo, pageQuery);
    }

    /**
     * 查询业务分类列表(全量,用于筛选下拉与树分组)
     */
    @SaCheckPermission("budget:subject:list")
    @GetMapping("/list")
    public R<List<BudgetCategoryVo>> list(BudgetCategoryBo bo) {
        return R.ok(categoryService.queryList(bo));
    }

    /**
     * 查询业务分类详细
     */
    @SaCheckPermission("budget:subject:query")
    @GetMapping("/{id}")
    public R<BudgetCategoryVo> getInfo(@PathVariable Long id) {
        return R.ok(categoryService.queryById(id));
    }

    /**
     * 自动生成分类编码(新增分类弹框调用)
     */
    @SaCheckPermission("budget:subject:list")
    @GetMapping("/nextCode")
    public R<String> nextCode() {
        return R.ok(categoryService.nextCode());
    }

    /**
     * 新增业务分类
     */
    @SaCheckPermission("budget:subject:add")
    @Log(title = "预算业务分类", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@RequestBody BudgetCategoryBo bo) {
        return toAjax(categoryService.insertByBo(bo));
    }

    /**
     * 修改业务分类
     */
    @SaCheckPermission("budget:subject:edit")
    @Log(title = "预算业务分类", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@RequestBody BudgetCategoryBo bo) {
        return toAjax(categoryService.updateByBo(bo));
    }

    /**
     * 批量删除业务分类
     */
    @SaCheckPermission("budget:subject:remove")
    @Log(title = "预算业务分类", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(categoryService.deleteWithValidByIds(CollUtil.toList(ids)));
    }
}