package org.dromara.budget.service;

import org.dromara.budget.domain.BudgetControlRule;
import org.dromara.budget.domain.vo.BudgetControlResult;
import org.dromara.budget.domain.vo.BudgetControlRuleVo;
import org.dromara.budget.domain.bo.BudgetControlRuleBo;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * 预算控制规则(事前拦截) Service接口
 */
public interface IBudgetControlRuleService {

    /**
     * 校验并新增控制规则
     */
    Boolean insertByBo(BudgetControlRuleBo bo);

    /**
     * 校验并修改控制规则
     */
    Boolean updateByBo(BudgetControlRuleBo bo);

    /**
     * 批量删除控制规则(逻辑删除)
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    /**
     * 查询控制规则列表
     */
    List<BudgetControlRuleVo> queryList(BudgetControlRuleBo bo);

    /**
     * 分页查询控制规则
     */
    TableDataInfo<BudgetControlRuleVo> queryPageList(BudgetControlRuleBo bo, PageQuery pageQuery);

    /**
     * 启停控制规则
     *
     * @param id      规则ID
     * @param enabled 是否启用
     */
    Boolean toggleEnabled(Long id, Boolean enabled);

    /**
     * 按(方案,单位,预算表)匹配最合适的启用规则；未命中返回 null(宽松不控制)
     */
    BudgetControlRule matchRule(Long planId, Long orgId, String templateCode);

    /**
     * 事前控制评估：结合控制口径计算参考执行/预算，判定等级与是否阻断。
     * budget<=0 或未命中启用规则时返回 GREEN、blocked=false(不控制)。
     *
     * @param quarters 季度执行额(q1..q4)，用于 CURRENT 当期口径
     */
    BudgetControlResult evaluate(Long planId, Long orgId, String templateCode,
                                 BigDecimal budget, BigDecimal executionSum, List<BigDecimal> quarters);
}