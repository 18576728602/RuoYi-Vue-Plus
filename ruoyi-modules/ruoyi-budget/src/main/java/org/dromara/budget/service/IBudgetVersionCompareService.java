package org.dromara.budget.service;

import org.dromara.budget.domain.bo.BudgetVersionCompareQuery;
import org.dromara.budget.domain.vo.BudgetVersionCompareResult;

/**
 * 预算版本对比
 *
 * <p>对比两个预算方案（年度版本）的同类科目预算差异，支持双栏展示、差异高亮与统计。
 *
 * @author Lion Li
 * @date 2026-09-14
 */
public interface IBudgetVersionCompareService {

    /**
     * 版本对比
     */
    BudgetVersionCompareResult compare(BudgetVersionCompareQuery q);
}