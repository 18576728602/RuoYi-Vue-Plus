package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.budget.domain.vo.BudgetExecutionVo;
import org.dromara.budget.service.IBudgetExecutionService;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 预算执行情况
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/execution")
public class BudgetExecutionController extends BaseController {

    private final IBudgetExecutionService budgetExecutionService;

    /**
     * 获取执行情况列表
     */
    @SaCheckPermission("budget:execution:list")
    @GetMapping("/list")
    public R<List<BudgetExecutionVo>> list(
        @RequestParam Long planId,
        @RequestParam(required = false) Long orgId,
        @RequestParam(required = false) String templateCode
    ) {
        return R.ok(budgetExecutionService.getExecutionList(planId, orgId, templateCode));
    }

    /**
     * 获取执行汇总统计
     */
    @SaCheckPermission("budget:execution:list")
    @GetMapping("/summary")
    public R<Map<String, Object>> summary(
        @RequestParam Long planId,
        @RequestParam(required = false) Long orgId
    ) {
        return R.ok(budgetExecutionService.getExecutionSummary(planId, orgId));
    }

    /**
     * 录入执行数（按季度）
     */
    @SaCheckPermission("budget:execution:input")
    @Log(title = "预算执行数录入", businessType = BusinessType.UPDATE)
    @PostMapping("/input")
    public R<Void> input(
        @RequestParam Long planId,
        @RequestParam Long orgId,
        @RequestParam String templateCode,
        @RequestParam String itemCode,
        @RequestParam(required = false) BigDecimal q1Amount,
        @RequestParam(required = false) BigDecimal q2Amount,
        @RequestParam(required = false) BigDecimal q3Amount,
        @RequestParam(required = false) BigDecimal q4Amount
    ) {
        budgetExecutionService.inputExecution(planId, orgId, templateCode, itemCode,
            q1Amount, q2Amount, q3Amount, q4Amount);
        return R.ok();
    }
}
