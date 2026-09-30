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
import org.dromara.budget.domain.vo.BudgetTemplateItemVo;
import org.dromara.budget.domain.bo.BudgetTemplateItemBo;
import org.dromara.budget.domain.bo.BudgetSubjectImportBo;
import org.dromara.budget.service.IBudgetTemplateItemService;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import java.util.Map;

/**
 * 预算模板科目
 *
 * @author Lion Li
 * @date 2026-08-29
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/templateItem")
public class BudgetTemplateItemController extends BaseController {

    private final IBudgetTemplateItemService budgetTemplateItemService;

    /**
     * 查询预算模板科目列表
     */
    @SaCheckPermission("budget:templateItem:list")
    @GetMapping("/list")
    public TableDataInfo<BudgetTemplateItemVo> list(BudgetTemplateItemBo bo, PageQuery pageQuery) {
        return budgetTemplateItemService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出预算模板科目列表
     */
    @SaCheckPermission("budget:templateItem:export")
    @Log(title = "预算模板科目", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BudgetTemplateItemBo bo, HttpServletResponse response) {
        List<BudgetTemplateItemVo> list = budgetTemplateItemService.queryList(bo);
        ExcelUtil.exportExcel(list, "预算模板科目", BudgetTemplateItemVo.class, response);
    }

    /**
     * 获取预算模板科目详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("budget:templateItem:query")
    @GetMapping("/{id}")
    public R<BudgetTemplateItemVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(budgetTemplateItemService.queryById(id));
    }

    /**
     * 新增预算模板科目
     */
    @SaCheckPermission("budget:templateItem:add")
    @Log(title = "预算模板科目", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody BudgetTemplateItemBo bo) {
        return toAjax(budgetTemplateItemService.insertByBo(bo));
    }

    /**
     * 修改预算模板科目
     */
    @SaCheckPermission("budget:templateItem:edit")
    @Log(title = "预算模板科目", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody BudgetTemplateItemBo bo) {
        return toAjax(budgetTemplateItemService.updateByBo(bo));
    }

    /**
     * 从科目主数据库导入科目到指定方案（复制挂接）
     *
     * @param bo 目标方案ID + 选中的科目主数据ID集合
     * @return 本次新增的方案科目数量
     */
    @SaCheckPermission("budget:templateItem:add")
    @Log(title = "预算模板科目", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/importFromMaster")
    public R<Integer> importFromMaster(@Validated @RequestBody BudgetSubjectImportBo bo) {
        return R.ok(budgetTemplateItemService.importFromMaster(bo.getPlanId(), bo.getSubjectIds()));
    }

    /**
     * 分页查询某方案的已停用科目（del_flag=1），用于还原管理
     */
    @SaCheckPermission("budget:templateItem:list")
    @GetMapping("/disabledList")
    public TableDataInfo<BudgetTemplateItemVo> disabledList(@NotNull(message = "方案ID不能为空") Long planId,
                                                            String templateCode,
                                                            PageQuery pageQuery) {
        return budgetTemplateItemService.queryDisabledPage(planId, templateCode, pageQuery);
    }

    /**
     * 查询参与填报的公司列表，用于科目"适用公司"范围选择
     */
    @SaCheckPermission("budget:templateItem:list")
    @GetMapping("/companies")
    public R<List<Map<String, Object>>> companies() {
        return R.ok(budgetTemplateItemService.listCompanies());
    }

    /**
     * 还原已停用科目（del_flag 1 -> 0）
     *
     * @param ids 主键串
     */
    @SaCheckPermission("budget:templateItem:edit")
    @Log(title = "预算模板科目", businessType = BusinessType.UPDATE)
    @PutMapping("/restore/{ids}")
    public R<Void> restore(@NotEmpty(message = "主键不能为空")
                           @PathVariable Long[] ids) {
        return toAjax(budgetTemplateItemService.restoreByIds(List.of(ids)));
    }

    /**
     * 删除预算模板科目（逻辑删除：del_flag 0 -> 1，即停用，可还原）
     *
     * @param ids 主键串
     */
    @SaCheckPermission("budget:templateItem:remove")
    @Log(title = "预算模板科目", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(budgetTemplateItemService.deleteWithValidByIds(List.of(ids), true));
    }
}
