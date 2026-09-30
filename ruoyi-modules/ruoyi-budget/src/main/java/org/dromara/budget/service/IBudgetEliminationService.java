package org.dromara.budget.service;

import java.util.List;
import java.util.Map;

/**
 * 抵销分录 Service接口
 *
 * 从已确认(CONFIRMED)的内部交易，结合抵销项目配置，自动生成逐笔抵销分录，供合并前透明核对。
 */
public interface IBudgetEliminationService {

    /**
     * 查询抵销分录清单
     *
     * @param planId          预算方案ID（必填）
     * @param transactionType 交易类型(IS/BS/CF)，可选
     * @param projectCode     抵销项目编码，可选
     */
    List<Map<String, Object>> getEliminationEntries(Long planId, String transactionType, String projectCode);
}