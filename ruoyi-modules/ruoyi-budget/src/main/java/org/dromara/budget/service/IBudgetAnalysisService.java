package org.dromara.budget.service;

import org.dromara.budget.domain.bo.BudgetAnalysisQuery;
import org.dromara.budget.domain.vo.BudgetAnalysisResult;
import org.dromara.budget.domain.vo.BudgetAnalysisOverview;

/**
 * 预算执行差异归因分析
 *
 * <p>参考成熟全面预算产品（用友BIP/元年E7）：将 预算数 vs 执行数 的差异
 * 按【单位】或【科目】维度汇总，计算差异率与贡献度，定位差异主因。
 *
 * @author Lion Li
 * @date 2026-09-14
 */
public interface IBudgetAnalysisService {

    /**
     * 差异概览卡：总预算/总执行/总差异/平均执行率
     */
    BudgetAnalysisOverview overview(BudgetAnalysisQuery q);

    /**
     * 差异归因列表：按 dimension 分组返回（UNIT=单位 ORG=科目），含差异额/差异率/贡献度排序
     */
    BudgetAnalysisResult analyze(BudgetAnalysisQuery q);
}