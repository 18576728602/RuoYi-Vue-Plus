package org.dromara.budget.service;

import java.util.List;
import java.util.Map;

/**
 * 预算执行情况（各单位执行矩阵） Service接口
 *
 * 按模板+方案，纵向=预算科目，横向=各填报单位，展示各单位预算执行进度对比
 */
public interface IBudgetExecMatrixService {

    /**
     * 获取所有预算表模板（排除01公司基本信息表）
     */
    List<Map<String, Object>> getTemplates();

    /**
     * 获取指定模板的执行情况矩阵数据
     *
     * @param planId       预算方案ID
     * @param templateCode 模板编号
     * @param quarter      季度维度：ALL / Q1 / Q2 / Q3 / Q4
     * @return { columns: 列定义, rows: 科目行(含 perDept 执行信息), columnTotals: 各单位底部合计, quarter: 季度 }
     */
    Map<String, Object> getExecutionMatrixData(Long planId, String templateCode, String quarter);
}