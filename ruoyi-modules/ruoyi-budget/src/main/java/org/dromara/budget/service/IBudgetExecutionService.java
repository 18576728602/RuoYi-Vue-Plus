package org.dromara.budget.service;

import org.dromara.budget.domain.vo.BudgetExecutionVo;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 预算执行情况 Service
 */
public interface IBudgetExecutionService {

    /**
     * 获取执行情况列表（按预算表分组）
     */
    List<BudgetExecutionVo> getExecutionList(Long planId, Long orgId, String templateCode);

    /**
     * 获取执行汇总统计
     */
    Map<String, Object> getExecutionSummary(Long planId, Long orgId);

    /**
     * 录入执行数（按季度）
     */
    void inputExecution(Long planId, Long orgId, String templateCode, String itemCode,
                        BigDecimal q1Amount, BigDecimal q2Amount, BigDecimal q3Amount, BigDecimal q4Amount);
}
