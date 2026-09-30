package org.dromara.budget.service;

import org.dromara.budget.domain.bo.BudgetScenarioBo;
import org.dromara.budget.domain.vo.BudgetScenarioResultVo;
import org.dromara.budget.domain.vo.BudgetScenarioVo;
import org.dromara.budget.domain.vo.BudgetSensitivityRow;

import java.util.List;

/**
 * 情景模拟与敏感性分析 Service
 *
 * @author Lion Li
 * @date 2026-09-15
 */
public interface IBudgetScenarioService {

    /**
     * 情景参数列表
     */
    List<BudgetScenarioVo> listScenarios(Long planId);

    /**
     * 保存情景参数（新增/更新）
     */
    void saveScenario(BudgetScenarioBo bo);

    /**
     * 删除情景
     */
    void deleteScenario(Long id);

    /**
     * 情景模拟结果（多情景对比：雷达图/瀑布图/指标表）
     */
    BudgetScenarioResultVo simulate(Long planId, Long orgId);

    /**
     * 敏感性分析（龙卷风图数据）
     */
    List<BudgetSensitivityRow> sensitivity(Long planId, Long orgId);
}