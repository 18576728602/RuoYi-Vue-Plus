package org.dromara.budget.service;

import java.util.List;
import java.util.Map;

/**
 * 预算工作台仪表盘 Service
 */
public interface IBudgetDashboardService {

    /**
     * 获取统计卡片数据
     *
     * @param planId 预算方案ID，为空则统计全部方案
     * @param orgId  填报单位ID，为空则统计全部单位
     */
    Map<String, Object> getStats(Long planId, Long orgId);

    /**
     * 获取各单位填报进度
     *
     * @param planId 预算方案ID，为空则统计全部方案
     * @param orgId  填报单位ID，为空则统计全部单位
     */
    List<Map<String, Object>> getFillProgress(Long planId, Long orgId);

    /**
     * 获取待办事项（全局任务，不随方案/单位过滤）
     */
    List<Map<String, Object>> getTodoList();

    /**
     * 获取最新动态
     *
     * @param planId 预算方案ID，为空则返回全部方案动态
     * @param orgId  填报单位ID，为空则返回全部单位动态
     */
    List<Map<String, Object>> getRecentActivities(Long planId, Long orgId);

    /**
     * 获取季度执行趋势（Q1~Q4 执行金额、预算总额、各季度执行率）
     *
     * @param planId 预算方案ID，为空则汇总全部方案
     * @param orgId  填报单位ID，为空则汇总全部单位
     */
    Map<String, Object> getQuarterTrend(Long planId, Long orgId);

    /**
     * 获取可选择填报单位列表（下拉数据源）
     */
    List<Map<String, Object>> getUnitOptions();
}