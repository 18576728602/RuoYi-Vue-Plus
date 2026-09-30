package org.dromara.budget.controller;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.dromara.common.web.core.BaseController;
import org.dromara.budget.service.IBudgetEliminationService;

/**
 * 抵销分录
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/elimination")
public class BudgetEliminationController extends BaseController {

    private final IBudgetEliminationService budgetEliminationService;

    /**
     * 查询抵销分录清单（仅已确认内部交易 → 自动生成）
     */
    @SaCheckPermission("budget:elimination:list")
    @GetMapping("/entries")
    public List<Map<String, Object>> entries(@RequestParam Long planId,
                                             @RequestParam(required = false) String transactionType,
                                             @RequestParam(required = false) String projectCode) {
        return budgetEliminationService.getEliminationEntries(planId, transactionType, projectCode);
    }
}