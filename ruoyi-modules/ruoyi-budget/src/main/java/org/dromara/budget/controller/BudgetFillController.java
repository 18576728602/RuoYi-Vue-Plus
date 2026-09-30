package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.budget.domain.bo.BudgetFillBo;
import org.dromara.budget.domain.vo.BudgetFillVo;
import org.dromara.budget.service.IBudgetFillService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 预算智能填报
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/fill")
public class BudgetFillController {

    private final IBudgetFillService budgetFillService;

    /**
     * 获取当前登录人的填报单位（含格式化显示名），用于非管理员填报页单位显示
     */
    @GetMapping("/myUnit")
    public R<Map<String, Object>> myUnit() {
        return R.ok(budgetFillService.getMyUnit());
    }

    /**
     * 获取填报数据
     * 根据当前登录用户的部门，自动过滤该部门负责的科目
     *
     * @param planId        预算方案ID
     * @param templateCode  预算表编号
     * @return 填报数据列表（含科目结构+已有填报数据）
     */
    @SaCheckPermission("budget:fill:list")
    @GetMapping("/getFillData")
    public R<List<BudgetFillVo>> getFillData(
        @NotNull(message = "预算方案不能为空") @RequestParam Long planId,
        @NotBlank(message = "预算表编号不能为空") @RequestParam String templateCode,
        @RequestParam(required = false) Long deptId) {
        return R.ok(budgetFillService.getFillData(planId, templateCode, deptId));
    }

    /**
     * 获取所有板块填报数据（一次性返回全部11个板块）
     */
    @SaCheckPermission("budget:fill:list")
    @GetMapping("/getAllFillData")
    public R<List<BudgetFillVo>> getAllFillData(
        @NotNull(message = "预算方案不能为空") @RequestParam Long planId,
        @RequestParam(required = false) Long deptId) {
        return R.ok(budgetFillService.getAllFillData(planId, deptId));
    }

    /**
     * 保存草稿（批量）
     */
    @SaCheckPermission("budget:fill:add")
    @Log(title = "预算填报保存草稿", businessType = BusinessType.INSERT)
    @PostMapping("/saveDraft")
    public R<Void> saveDraft(@Validated(AddGroup.class) @RequestBody BudgetFillBo bo) {
        budgetFillService.saveDraft(bo);
        return R.ok();
    }

    /**
     * 提交审批（批量）
     * 先保存草稿，再将状态改为已提交
     */
    @SaCheckPermission("budget:fill:submit")
    @Log(title = "预算填报提交", businessType = BusinessType.UPDATE)
    @PostMapping("/submit")
    public R<Void> submit(@Validated(EditGroup.class) @RequestBody BudgetFillBo bo) {
        budgetFillService.submit(bo);
        return R.ok();
    }


}
