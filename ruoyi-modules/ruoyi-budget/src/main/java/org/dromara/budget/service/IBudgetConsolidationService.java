package org.dromara.budget.service;

import java.util.List;
import java.util.Map;

/**
 * 合并三表 Service接口
 */
public interface IBudgetConsolidationService {

    /**
     * 获取合并报表数据
     *
     * @param planId         预算方案ID
     * @param statementType  报表类型: IS-利润表, BS-资产负债表, CF-现金流量表
     * @param orgId          申报单位ID；为空时按全集团合并
     * @return 合并报表行列表
     */
    List<Map<String, Object>> getConsolidatedStatement(Long planId, String statementType, Long orgId);

    /**
     * 获取合并范围信息
     */
    Map<String, Object> getConsolidationScope(Long planId, Long orgId);

    /**
     * 获取抵销汇总信息
     */
    List<Map<String, Object>> getEliminationSummary(Long planId, Long orgId);

    /**
     * 保存手动抵销调整
     *
     * @param planId         预算方案ID
     * @param statementType  报表类型: IS-利润表, BS-资产负债表, CF-现金流量表
     * @param adjustments    抵销调整明细行列表，每行含 itemCode / adjustAmount
     */
    void saveAdjustments(Long planId, String statementType, List<Map<String, Object>> adjustments);
}
