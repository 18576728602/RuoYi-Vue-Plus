package org.dromara.budget.service;

import org.dromara.budget.domain.bo.BudgetFillBo;
import org.dromara.budget.domain.vo.BudgetFillVo;

import java.util.List;
import java.util.Map;

/**
 * 预算智能填报 Service接口
 */
public interface IBudgetFillService {

    /**
     * 获取当前登录人的填报单位（含格式化显示名）
     *
     * @return 当前登录人所在单位信息
     */
    Map<String, Object> getMyUnit();

    /**
     * 获取填报数据（单个预算表）
     *
     * @param planId        预算方案ID
     * @param templateCode  预算表编号
     * @param deptId        部门ID（admin传，普通用户自动获取）
     * @return 填报数据列表
     */
    List<BudgetFillVo> getFillData(Long planId, String templateCode, Long deptId);

    /**
     * 获取所有板块填报数据
     *
     * @param planId  预算方案ID
     * @param deptId  部门ID（admin传，普通用户自动获取）
     * @return 全部板块填报数据
     */
    List<BudgetFillVo> getAllFillData(Long planId, Long deptId);

    /**
     * 批量保存草稿
     */
    void saveDraft(BudgetFillBo bo);

    /**
     * 批量提交审批
     */
    void submit(BudgetFillBo bo);
}
