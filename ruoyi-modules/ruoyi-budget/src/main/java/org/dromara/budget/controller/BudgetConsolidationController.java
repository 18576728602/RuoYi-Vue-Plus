package org.dromara.budget.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.budget.service.IBudgetConsolidationService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 合并三表 Controller
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/consolidated")
public class BudgetConsolidationController {

    private final IBudgetConsolidationService consolidationService;

    /**
     * 获取合并报表数据
     *
     * @param planId        预算方案ID
     * @param statementType 报表类型: IS-利润表, BS-资产负债表, CF-现金流量表
     * @param orgId         申报单位ID；为空时按全集团合并
     */
    @GetMapping("/statement")
    public R<List<Map<String, Object>>> getStatement(
            @RequestParam Long planId,
            @RequestParam String statementType,
            @RequestParam(required = false) Long orgId) {
        return R.ok(consolidationService.getConsolidatedStatement(planId, statementType, orgId));
    }

    /**
     * 获取合并范围信息
     */
    @GetMapping("/scope")
    public R<Map<String, Object>> getScope(@RequestParam Long planId, @RequestParam(required = false) Long orgId) {
        return R.ok(consolidationService.getConsolidationScope(planId, orgId));
    }

    /**
     * 获取抵销汇总信息
     */
    @GetMapping("/elimination-summary")
    public R<List<Map<String, Object>>> getEliminationSummary(@RequestParam Long planId, @RequestParam(required = false) Long orgId) {
        return R.ok(consolidationService.getEliminationSummary(planId, orgId));
    }

    /**
     * 保存手动抵销调整
     */
    @PostMapping("/save-adjustments")
    public R<Void> saveAdjustments(@RequestBody Map<String, Object> body) {
        Long planId = body.get("planId") == null ? null : Long.valueOf(body.get("planId").toString());
        String statementType = body.get("statementType") == null ? null : body.get("statementType").toString();
        //noinspection unchecked
        List<Map<String, Object>> adjustments =
            (List<Map<String, Object>>) body.getOrDefault("adjustments", new java.util.ArrayList<Map<String, Object>>());
        consolidationService.saveAdjustments(planId, statementType, adjustments);
        return R.ok();
    }
}
