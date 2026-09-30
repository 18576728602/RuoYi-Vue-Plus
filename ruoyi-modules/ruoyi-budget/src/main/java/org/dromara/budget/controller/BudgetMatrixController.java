package org.dromara.budget.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.service.IBudgetMatrixService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 预算主表（各单位矩阵） Controller
 *
 * 按模板编号查询所有单位的预算数据，形成矩阵视图：
 * 行 = 预算科目，列 = 各单位
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/matrix")
public class BudgetMatrixController {

    private final IBudgetMatrixService matrixService;

    /**
     * 获取所有模板列表（用于Tab展示）
     */
    @GetMapping("/templates")
    public R<List<Map<String, Object>>> getTemplates() {
        return R.ok(matrixService.getTemplates());
    }

    /**
     * 获取指定模板的矩阵数据
     *
     * @param planId       预算方案ID
     * @param templateCode 模板编号（如"02"）
     * @return 矩阵数据（行=科目，列=各单位）
     */
    @GetMapping("/data")
    public R<Map<String, Object>> getMatrixData(
        @RequestParam Long planId,
        @RequestParam String templateCode
    ) {
        return R.ok(matrixService.getMatrixData(planId, templateCode));
    }
}
