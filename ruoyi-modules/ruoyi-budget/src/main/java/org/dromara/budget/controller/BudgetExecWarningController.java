package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import lombok.RequiredArgsConstructor;
import org.dromara.budget.domain.bo.BudgetWarningRecordBo;
import org.dromara.budget.service.IBudgetExecWarningService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 预算执行预警 控制层（智能预警：多源融合 + 闭环管理）
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/warning")
public class BudgetExecWarningController {

    private final IBudgetExecWarningService budgetExecWarningService;

    /**
     * 获取预算执行预警清单（多源融合）
     */
    @SaCheckPermission(value = {"budget:warning:list", "budget:warning:manage"}, mode = SaMode.OR)
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list(@RequestParam(required = false) Long planId,
                                             @RequestParam(required = false) Long orgId,
                                             @RequestParam(required = false) Integer threshold) {
        return R.ok(budgetExecWarningService.getWarningList(planId, orgId, threshold));
    }

    /**
     * 查询预警闭环处理记录
     */
    @SaCheckPermission(value = {"budget:warning:list", "budget:warning:manage"}, mode = SaMode.OR)
    @GetMapping("/record/list")
    public R<List<Map<String, Object>>> recordList(@RequestParam(required = false) Long planId,
                                                   @RequestParam(required = false) Long orgId,
                                                   @RequestParam(required = false) String level,
                                                   @RequestParam(required = false) String status) {
        return R.ok(budgetExecWarningService.listRecords(planId, orgId, level, status));
    }

    /**
     * 新建预警闭环记录
     */
    @SaCheckPermission("budget:warning:manage")
    @PostMapping("/record/create")
    public R<Long> createRecord(@RequestBody BudgetWarningRecordBo bo) {
        return R.ok(budgetExecWarningService.createRecord(bo));
    }

    /**
     * 处置预警（确认/整改/跟踪，留痕）
     */
    @SaCheckPermission("budget:warning:manage")
    @PostMapping("/record/handle")
    public R<Void> handleRecord(@RequestBody BudgetWarningRecordBo bo) {
        budgetExecWarningService.handleRecord(bo);
        return R.ok();
    }

    /**
     * 升级预警（提升通知对象）
     */
    @SaCheckPermission("budget:warning:manage")
    @PostMapping("/record/escalate")
    public R<Void> escalateRecord(@RequestBody BudgetWarningRecordBo bo) {
        budgetExecWarningService.escalateRecord(bo);
        return R.ok();
    }

    /**
     * 解除预警（关闭闭环）
     */
    @SaCheckPermission("budget:warning:manage")
    @PostMapping("/record/close")
    public R<Void> closeRecord(@RequestParam Long recordId, @RequestParam(required = false) String result) {
        budgetExecWarningService.closeRecord(recordId, result);
        return R.ok();
    }
}