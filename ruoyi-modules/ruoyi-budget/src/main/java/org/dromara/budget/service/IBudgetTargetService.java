package org.dromara.budget.service;

import org.dromara.budget.domain.bo.BudgetTargetBo;

import java.util.List;
import java.util.Map;

/**
 * 预算目标下达 Service（一下）
 *
 * <p>PRD 7.1.2 第一阶段：集团预算管理员下达预算目标基准线，状态草稿→已下达，按版本维护。</p>
 */
public interface IBudgetTargetService {

    /**
     * 目标下达公司列表（含各公司目标状态摘要）
     */
    List<Map<String, Object>> listCompanyTargets(Long planId);

    /**
     * 查询某公司(+模板/版本)目标明细；versionNo 为空时为最新已下达版本或草稿
     */
    List<Map<String, Object>> getDetail(Long planId, Long deptId, String templateCode, Integer versionNo);

    /**
     * 集团：按 方案+公司 生成目标编辑用科目集（沿用该公司适用的模板科目）
     */
    List<Map<String, Object>> buildItems(Long planId, Long deptId, String templateCode);

    /**
     * 集团：保存目标草稿（整体覆盖当前草稿版本；若已有下达版本则开启新版本）
     */
    void saveDraft(BudgetTargetBo bo);

    /**
     * 集团：下达目标（草稿→已下达）
     */
    void publishTarget(Long planId, Long deptId);

    /**
     * 集团：某公司目标版本历史
     */
    List<Map<String, Object>> listHistory(Long planId, Long deptId);

    /**
     * 子公司：查看本公司目标（仅已下达版本，只读）
     */
    List<Map<String, Object>> getMyTargets(Long planId, Long deptId, String templateCode);

    /**
     * 目标 vs 实际填报 对比（二下：与下达目标对比，差异率/超区间高亮）
     */
    Map<String, Object> compareFill(Long planId, Long deptId, String templateCode);
}