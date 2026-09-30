package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.budget.domain.bo.BudgetVersionCompareQuery;
import org.dromara.budget.domain.vo.BudgetVersionCompareExportVo;
import org.dromara.budget.domain.vo.BudgetVersionCompareResult;
import org.dromara.budget.service.IBudgetVersionCompareService;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 预算版本对比
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/versionCompare")
public class BudgetVersionCompareController {

    private final IBudgetVersionCompareService budgetVersionCompareService;

    /**
     * 版本对比
     */
    @SaCheckPermission("budget:versionCompare:list")
    @GetMapping("/compare")
    public R<BudgetVersionCompareResult> compare(@Validated BudgetVersionCompareQuery q) {
        return R.ok(budgetVersionCompareService.compare(q));
    }

    /**
     * 版本对比结果导出(XLSX)
     */
    @SaCheckPermission("budget:versionCompare:list")
    @Log(title = "预算版本对比", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(@Validated BudgetVersionCompareQuery q, HttpServletResponse response) {
        BudgetVersionCompareResult result = budgetVersionCompareService.compare(q);
        List<BudgetVersionCompareExportVo> list = new ArrayList<>();
        if (result.getRows() != null) {
            for (BudgetVersionCompareResult.CompareRow row : result.getRows()) {
                BudgetVersionCompareExportVo vo = new BudgetVersionCompareExportVo();
                vo.setOrgName(row.getOrgName());
                vo.setTemplateCode(row.getTemplateCode());
                vo.setTemplateName(row.getTemplateName());
                vo.setItemCode(row.getItemCode());
                vo.setItemName(row.getItemName());
                vo.setAmountA(row.getAmountA());
                vo.setAmountB(row.getAmountB());
                vo.setDiff(row.getDiff());
                vo.setDiffRate(row.getDiffRate());
                if (row.isRemoved()) {
                    vo.setChangeType("减少");
                } else if (row.isAdded()) {
                    vo.setChangeType("新增");
                } else if (row.isChanged()) {
                    vo.setChangeType("变更");
                } else {
                    vo.setChangeType("不变");
                }
                list.add(vo);
            }
        }
        String sheetName = result.getPlanNameA() + " vs " + result.getPlanNameB();
        ExcelUtil.exportExcel(list, sheetName, BudgetVersionCompareExportVo.class, response);
    }
}