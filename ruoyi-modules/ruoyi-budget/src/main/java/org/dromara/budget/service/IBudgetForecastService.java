package org.dromara.budget.service;

import org.dromara.budget.domain.bo.BudgetForecastQuery;
import org.dromara.budget.domain.vo.BudgetForecastDetailVo;
import org.dromara.budget.domain.vo.BudgetForecastVersionVo;

import java.util.List;

/**
 * 滚动预测 Service
 *
 * @author Lion Li
 * @date 2026-09-15
 */
public interface IBudgetForecastService {

    /**
     * 生成/重新生成一次滚动预测（按刷新时点截取实际，剩余季度推算）
     * 返回预测主表ID
     */
    Long generateForecast(BudgetForecastQuery query);

    /**
     * 预测版本列表（按方案+单位）
     */
    List<BudgetForecastVersionVo> listVersions(Long planId, Long orgId);

    /**
     * 预测版本详情（含明细行+趋势图数据）
     */
    BudgetForecastDetailVo getForecastDetail(Long forecastId);

    /**
     * 删除预测版本
     */
    void deleteForecast(Long forecastId);
}