package org.dromara.budget.service;

import org.dromara.budget.domain.vo.BudgetPlanVo;
import org.dromara.budget.domain.bo.BudgetPlanBo;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;

/**
 * 预算方案Service接口
 *
 * @author Lion Li
 * @date 2026-08-29
 */
public interface IBudgetPlanService {

    /**
     * 查询预算方案
     *
     * @param id 主键
     * @return 预算方案
     */
    BudgetPlanVo queryById(Long id);

    /**
     * 分页查询预算方案列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 预算方案分页列表
     */
    TableDataInfo<BudgetPlanVo> queryPageList(BudgetPlanBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的预算方案列表
     *
     * @param bo 查询条件
     * @return 预算方案列表
     */
    List<BudgetPlanVo> queryList(BudgetPlanBo bo);

    /**
     * 新增预算方案
     *
     * @param bo 预算方案
     * @return 新增成功的方案ID
     */
    Long insertByBo(BudgetPlanBo bo);

    /**
     * 修改预算方案
     *
     * @param bo 预算方案
     * @return 是否修改成功
     */
    Boolean updateByBo(BudgetPlanBo bo);

    /**
     * 变更方案状态（发布/归档/关闭）
     * 发布：仅草稿 → 已发布，同一年度唯一
     * 归档：仅已发布 → 已归档，且须已到达填报截止时间
     * 关闭：仅已发布/已归档 → 已关闭
     *
     * @param bo 含方案ID与目标状态的BO
     */
    void changeStatus(BudgetPlanBo bo);

    /**
     * 校验并批量删除预算方案信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
