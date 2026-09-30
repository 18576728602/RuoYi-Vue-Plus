package org.dromara.budget.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.service.IBudgetExecMatrixService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 预算执行情况（各单位执行矩阵） Controller
 *
 * 按模板+方案展示各单位预算执行进度矩阵：
 * 行 = 预算科目，列 = 各单位，单元格 = {预算, 执行, 执行率}
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/execmatrix")
public class BudgetExecMatrixController {

    private final IBudgetExecMatrixService execMatrixService;

    /**
     * 获取所有模板列表（用于Tab展示，排除01公司基本信息）
     */
    @GetMapping("/templates")
    public R<List<Map<String, Object>>> getTemplates() {
        return R.ok(execMatrixService.getTemplates());
    }

    /**
     * 获取指定模板的执行情况矩阵数据
     *
     * @param planId       预算方案ID
     * @param templateCode 模板编号（如"02"）
     * @param quarter      季度维度：ALL / Q1 / Q2 / Q3 / Q4
     */
    @GetMapping("/data")
    public R<Map<String, Object>> getExecutionMatrixData(
        @RequestParam Long planId,
        @RequestParam String templateCode,
        @RequestParam(required = false, defaultValue = "ALL") String quarter
    ) {
        return R.ok(execMatrixService.getExecutionMatrixData(planId, templateCode, quarter));
    }
}