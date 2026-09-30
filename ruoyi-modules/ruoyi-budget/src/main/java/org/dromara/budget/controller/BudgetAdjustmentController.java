package org.dromara.budget.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.*;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.budget.domain.bo.AdjustmentApproveBo;
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
import org.dromara.budget.domain.vo.BudgetAdjustmentVo;
import org.dromara.budget.domain.bo.BudgetAdjustmentBo;
import org.dromara.budget.service.IBudgetAdjustmentService;
import org.dromara.common.mybatis.core.page.TableDataInfo;

/**
 * 预算调整记录
 *
 * @author Lion Li
 * @date 2026-08-29
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/adjustment")
public class BudgetAdjustmentController extends BaseController {

    private final IBudgetAdjustmentService budgetAdjustmentService;

    /**
     * 查询预算调整记录列表
     */
    @SaCheckPermission("budget:adjustment:list")
    @GetMapping("/list")
    public TableDataInfo<BudgetAdjustmentVo> list(BudgetAdjustmentBo bo, PageQuery pageQuery) {
        return budgetAdjustmentService.queryPageList(bo, pageQuery);
    }

    /**
     * 查询某单位某方案下已审批通过的预算表编号列表（预算调整申请时预算表/科目下拉按本单位已审批表过滤，子公司看不到集团本部表）
     */
    @SaCheckPermission("budget:adjustment:list")
    @GetMapping("/approvedTemplates")
    public R<List<String>> approvedTemplates(@RequestParam Long planId,
                                          @RequestParam(required = false) Long orgId) {
        return R.ok(budgetAdjustmentService.listApprovedTemplateCodes(planId, orgId));
    }

    /**
     * 调整影响分析：预估本次调整对本公司及集团的影响，并追溯该科目历史调整
     */
    @SaCheckPermission("budget:adjustment:add")
    @GetMapping("/impact")
    public R<org.dromara.budget.domain.vo.BudgetAdjustmentImpactVo> impact(@RequestParam @NotNull(message = "预算方案ID不能为空") Long planId,
                                                                           @RequestParam @NotNull(message = "调整单位不能为空") Long orgId,
                                                                           @RequestParam @NotBlank(message = "预算表编号不能为空") String templateCode,
                                                                           @RequestParam @NotBlank(message = "科目编码不能为空") String itemCode,
                                                                           @RequestParam(required = false) java.math.BigDecimal originalAmount,
                                                                           @RequestParam @NotNull(message = "调整金额不能为空") java.math.BigDecimal adjustAmount) {
        return R.ok(budgetAdjustmentService.analyzeImpact(planId, orgId, templateCode, itemCode, originalAmount, adjustAmount));
    }

    /**
     * 导出预算调整记录列表
     */
    @SaCheckPermission("budget:adjustment:export")
    @Log(title = "预算调整记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BudgetAdjustmentBo bo, HttpServletResponse response) {
        List<BudgetAdjustmentVo> list = budgetAdjustmentService.queryList(bo);
        ExcelUtil.exportExcel(list, "预算调整记录", BudgetAdjustmentVo.class, response);
    }

    /**
     * 获取预算调整记录详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("budget:adjustment:query")
    @GetMapping("/{id}")
    public R<BudgetAdjustmentVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(budgetAdjustmentService.queryById(id));
    }

    /**
     * 新增预算调整记录
     */
    @SaCheckPermission("budget:adjustment:add")
    @Log(title = "预算调整记录", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody BudgetAdjustmentBo bo) {
        return toAjax(budgetAdjustmentService.insertByBo(bo));
    }

    /**
     * 修改预算调整记录
     */
    @SaCheckPermission("budget:adjustment:edit")
    @Log(title = "预算调整记录", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody BudgetAdjustmentBo bo) {
        return toAjax(budgetAdjustmentService.updateByBo(bo));
    }

    /**
     * 删除预算调整记录
     *
     * @param ids 主键串
     */
    @SaCheckPermission("budget:adjustment:remove")
    @Log(title = "预算调整记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(budgetAdjustmentService.deleteWithValidByIds(List.of(ids), true));
    }

    /**
     * 审批预算调整申请
     */
    @SaCheckPermission("budget:adjustment:approve")
    @Log(title = "预算调整审批", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/approve")
    public R<Void> approve(@Validated @RequestBody AdjustmentApproveBo bo) {
        return toAjax(budgetAdjustmentService.approve(bo));
    }


}
