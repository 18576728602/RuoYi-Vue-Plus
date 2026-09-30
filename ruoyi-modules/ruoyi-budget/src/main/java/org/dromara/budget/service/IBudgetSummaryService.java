package org.dromara.budget.service;

import org.dromara.budget.domain.vo.BudgetSummaryVo;

/**
 * 预算汇总看板 Service
 */
public interface IBudgetSummaryService {

    /**
     * 获取集团总览
     */
    BudgetSummaryVo.Overview getOverview(Long planId);

    /**
     * 获取各单位填报汇总
     */
    java.util.List<BudgetSummaryVo.DeptSummary> getDeptSummaryList(Long planId);

    /**
     * 获取各板块汇总
     */
    java.util.List<BudgetSummaryVo.TemplateSummary> getTemplateSummaryList(Long planId);
}
