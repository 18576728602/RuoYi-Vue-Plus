package org.dromara.budget.service;

import org.dromara.budget.domain.bo.BudgetFieldVisibilityBo;
import org.dromara.budget.domain.vo.BudgetFieldVisibilityVo;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 预算表字段可见性规则 Service 接口
 *
 * @author Lion Li
 * @date 2026-09-28
 */
public interface IBudgetFieldVisibilityService {

    /**
     * 分页查询规则列表
     */
    TableDataInfo<BudgetFieldVisibilityVo> queryPage(Long templateId, Long planId, Integer budgetYear, PageQuery pageQuery);

    /**
     * 查询指定预算表/方案/年度的所有规则
     */
    List<BudgetFieldVisibilityVo> queryRules(Long templateId, Long planId, Integer budgetYear);

    /**
     * 解析某子公司对一批科目(字段)的最终权限
     * <p>
     * 匹配维度优先级（具体度从高到低）：
     * <ol>
     *   <li>指定预算表 + 指定方案 + 指定年度（最具体）</li>
     *   <li>指定预算表 + 指定方案 + 跨年度</li>
     *   <li>指定预算表 + 通用方案(0) + 指定年度</li>
     *   <li>指定预算表 + 通用方案(0) + 跨年度</li>
     *   <li>全部预算表(null) + 指定方案 + 指定年度</li>
     *   <li>... 以此类推，维度越具体优先级越高</li>
     * </ol>
     * 未命中任何规则的科目不出现在返回 map 中（前端按默认 EDIT 处理）。
     *
     * @param templateId  预算表ID（可选，空则只匹配"全部预算表"级规则）
     * @param planId      方案ID（可选，空则只匹配 plan_id=0 的通用规则）
     * @param budgetYear  预算年度（可选，空则只匹配 budget_year IS NULL 的跨年度规则）
     * @param orgId       目标子公司ID（必填）
     * @param subjectIds  科目主数据ID集合（必填）
     * @return Map<科目主数据ID, 权限值 HIDE/READONLY/EDIT>；未命中的科目不在 map 中
     */
    Map<Long, String> resolvePermissions(Long templateId, Long planId, Integer budgetYear,
                                          Long orgId, Collection<Long> subjectIds);

    /**
     * 根据ID查询单条规则
     */
    BudgetFieldVisibilityVo queryById(Long id);

    /**
     * 保存规则（新增或更新）。
     * 按 科目×子公司×预算表×方案×年度 唯一键做幂等 upsert。
     */
    Boolean saveRule(BudgetFieldVisibilityBo bo);

    /**
     * 批量删除规则
     */
    Boolean removeByIds(Collection<Long> ids);
}
