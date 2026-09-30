package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.budget.domain.bo.BudgetFieldVisibilityBo;
import org.dromara.budget.domain.vo.BudgetFieldVisibilityVo;
import org.dromara.budget.service.IBudgetFieldVisibilityService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 预算表字段可见性规则
 *
 * @author Lion Li
 * @date 2026-09-28
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/fieldVisibility")
public class BudgetFieldVisibilityController extends BaseController {

    private final IBudgetFieldVisibilityService fieldVisibilityService;

    /**
     * 查询字段可见性规则列表（按预算表+方案+年度筛选）
     */
    @SaCheckPermission("budget:fieldVisibility:list")
    @GetMapping("/list")
    public TableDataInfo<BudgetFieldVisibilityVo> list(
        @RequestParam(required = false) Long templateId,
        @RequestParam(required = false) Long planId,
        @RequestParam(required = false) Integer budgetYear,
        PageQuery pageQuery) {
        return fieldVisibilityService.queryPage(templateId, planId, budgetYear, pageQuery);
    }

    /**
     * 解析某子公司对一批填报字段的最终权限（填报页取显隐、提交时越权校验）
     *
     * @param templateId  预算表ID（可选，空则只匹配"全部预算表"级规则）
     * @param planId      方案ID（可选，空则只匹配 plan_id=0 的通用规则）
     * @param budgetYear  预算年度（可选，空则只匹配 budget_year IS NULL 的跨年度规则）
     * @param orgId       子公司ID（必填）
     * @param subjectIds  科目主数据ID集合（必填）
     * @return Map<科目主数据ID, 权限值：HIDE/READONLY/EDIT>
     */
    @SaCheckPermission("budget:fieldVisibility:list")
    @GetMapping("/resolve")
    public R<Map<Long, String>> resolve(
        @RequestParam(required = false) Long templateId,
        @RequestParam(required = false) Long planId,
        @RequestParam(required = false) Integer budgetYear,
        @RequestParam Long orgId,
        @RequestParam List<Long> subjectIds) {
        return R.ok(fieldVisibilityService.resolvePermissions(templateId, planId, budgetYear, orgId, subjectIds));
    }

    /**
     * 导出字段可见性规则列表
     */
    @SaCheckPermission("budget:fieldVisibility:export")
    @Log(title = "字段可见性规则", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BudgetFieldVisibilityBo bo, HttpServletResponse response) {
        // 暂不实现批量导出
    }

    /**
     * 获取字段可见性规则详细信息
     */
    @SaCheckPermission("budget:fieldVisibility:query")
    @GetMapping("/{id}")
    public R<BudgetFieldVisibilityVo> getInfo(@PathVariable Long id) {
        return R.ok(fieldVisibilityService.queryById(id));
    }

    /**
     * 新增/更新字段可见性规则（按 科目×公司×表×方案×年度 唯一键幂等）
     */
    @SaCheckPermission("budget:fieldVisibility:add")
    @Log(title = "字段可见性规则", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody BudgetFieldVisibilityBo bo) {
        return toAjax(fieldVisibilityService.saveRule(bo));
    }

    /**
     * 修改字段可见性规则
     */
    @SaCheckPermission("budget:fieldVisibility:edit")
    @Log(title = "字段可见性规则", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody BudgetFieldVisibilityBo bo) {
        return toAjax(fieldVisibilityService.saveRule(bo));
    }

    /**
     * 删除字段可见性规则
     */
    @SaCheckPermission("budget:fieldVisibility:remove")
    @Log(title = "字段可见性规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Collection<Long> ids) {
        return toAjax(fieldVisibilityService.removeByIds(ids));
    }
}
