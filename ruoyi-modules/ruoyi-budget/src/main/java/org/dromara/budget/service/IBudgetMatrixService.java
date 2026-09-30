package org.dromara.budget.service;

import java.util.List;
import java.util.Map;

/**
 * 预算主表（各单位矩阵） Service接口
 */
public interface IBudgetMatrixService {

    /**
     * 获取所有模板列表
     */
    List<Map<String, Object>> getTemplates();

    /**
     * 获取矩阵数据
     *
     * @param planId       预算方案ID
     * @param templateCode 模板编号
     * @return { columns: 单位列, rows: 科目行数据, summaryRow: 合计行 }
     */
    Map<String, Object> getMatrixData(Long planId, String templateCode);
}
