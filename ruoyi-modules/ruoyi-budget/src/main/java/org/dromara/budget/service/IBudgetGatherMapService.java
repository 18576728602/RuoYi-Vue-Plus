package org.dromara.budget.service;

import org.dromara.budget.domain.vo.BudgetGatherMapVo;
import org.dromara.budget.domain.bo.BudgetGatherMapBo;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 预算科目归集映射Service接口
 *
 * @author Lion Li
 * @date 2026-09-16
 */
public interface IBudgetGatherMapService {

    /**
     * 查询预算科目归集映射
     *
     * @param id 主键
     * @return 预算科目归集映射
     */
    BudgetGatherMapVo queryById(Long id);

    /**
     * 分页查询预算科目归集映射列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 预算科目归集映射分页列表
     */
    TableDataInfo<BudgetGatherMapVo> queryPageList(BudgetGatherMapBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的预算科目归集映射列表
     *
     * @param bo 查询条件
     * @return 预算科目归集映射列表
     */
    List<BudgetGatherMapVo> queryList(BudgetGatherMapBo bo);

    /**
     * 新增预算科目归集映射
     *
     * @param bo 预算科目归集映射
     * @return 是否新增成功
     */
    Boolean insertByBo(BudgetGatherMapBo bo);

    /**
     * 修改预算科目归集映射
     *
     * @param bo 预算科目归集映射
     * @return 是否修改成功
     */
    Boolean updateByBo(BudgetGatherMapBo bo);

    /**
     * 校验并批量删除预算科目归集映射信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    /**
     * 执行归集：把指定方案下本部内设部门填报的17表明细，按映射累加到本部06表目标科目
     *
     * @param planId 方案ID
     * @return 归集覆盖的部门数据行数(写入/更新 budget_data 的行数)
     */
    Integer gather(Long planId);

    /**
     * 执行草稿归集：取「填报中（草稿/提交）」的源数据，写入目标为 DRAFT（本部填报数据）
     *
     * @param planId 方案ID
     * @return 归集覆盖的部门数据行数
     */
    Integer gatherFill(Long planId);

    /**
     * 查询「集团本部」直属的内设部门，作为归集源部门下拉数据源
     *
     * @return [{deptId, deptName}]
     */
    List<Map<String, Object>> listInnerDepts();
}