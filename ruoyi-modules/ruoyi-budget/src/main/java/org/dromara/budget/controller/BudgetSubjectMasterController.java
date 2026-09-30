package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.budget.domain.bo.BudgetSubjectMasterBo;
import org.dromara.budget.domain.vo.BudgetSubjectMasterVo;
import org.dromara.budget.service.IBudgetSubjectMasterService;
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
 * 预算科目主数据(科目明细)
 *
 * @author Lion Li
 * @date 2026-09-15
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/subject")
public class BudgetSubjectMasterController extends BaseController {

    private final IBudgetSubjectMasterService budgetSubjectMasterService;

    /**
     * 分页查询科目主数据列表
     */
    @SaCheckPermission("budget:subject:list")
    @GetMapping("/list")
    public TableDataInfo<BudgetSubjectMasterVo> list(BudgetSubjectMasterBo bo, PageQuery pageQuery) {
        return budgetSubjectMasterService.queryPageList(bo, pageQuery);
    }

    /**
     * 查询科目主数据列表(全量,用于树形展示)
     */
    @SaCheckPermission("budget:subject:list")
    @GetMapping("/queryList")
    public R<List<BudgetSubjectMasterVo>> queryList(BudgetSubjectMasterBo bo) {
        return R.ok(budgetSubjectMasterService.queryList(bo));
    }

    /**
     * 自动生成科目编码(新增时调用,规则由后端统一)
     */
    @SaCheckPermission("budget:subject:list")
    @GetMapping("/nextCode")
    public R<String> nextCode(@RequestParam(value = "templateCode", required = false) String templateCode,
                              @RequestParam(value = "parentCode", required = false) String parentCode,
                              @RequestParam(value = "subjectType", required = false) String subjectType,
                              @RequestParam(value = "orgId", required = false) Long orgId) {
        return R.ok(budgetSubjectMasterService.nextCode(templateCode, parentCode, subjectType, orgId));
    }

    /**
     * 查询科目主数据详细
     */
    @SaCheckPermission("budget:subject:query")
    @GetMapping("/{id}")
    public R<BudgetSubjectMasterVo> getInfo(@PathVariable Long id) {
        return R.ok(budgetSubjectMasterService.queryById(id));
    }

    /**
     * 新增科目主数据
     */
    @SaCheckPermission("budget:subject:add")
    @Log(title = "预算科目主数据", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody BudgetSubjectMasterBo bo) {
        return toAjax(budgetSubjectMasterService.insertByBo(bo));
    }

    /**
     * 修改科目主数据
     */
    @SaCheckPermission("budget:subject:edit")
    @Log(title = "预算科目主数据", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody BudgetSubjectMasterBo bo) {
        return toAjax(budgetSubjectMasterService.updateByBo(bo));
    }

    /**
     * 删除科目主数据
     */
    @SaCheckPermission("budget:subject:remove")
    @Log(title = "预算科目主数据", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(budgetSubjectMasterService.deleteWithValidByIds(List.of(ids), true));
    }

    /**
     * 启用/停用科目主数据
     */
    @SaCheckPermission("budget:subject:edit")
    @Log(title = "预算科目主数据", businessType = BusinessType.UPDATE)
    @PutMapping("/valid/{id}/{validFlag}")
    public R<Void> valid(@PathVariable Long id, @PathVariable String validFlag) {
        return toAjax(budgetSubjectMasterService.changeValid(id, validFlag));
    }

    /**
     * 查询科目主数据被方案/预算表挂接引用的次数(停用前提示)
     */
    @SaCheckPermission("budget:subject:query")
    @GetMapping("/refCount/{id}")
    public R<Long> refCount(@PathVariable Long id) {
        return R.ok(budgetSubjectMasterService.countRefs(id));
    }
}