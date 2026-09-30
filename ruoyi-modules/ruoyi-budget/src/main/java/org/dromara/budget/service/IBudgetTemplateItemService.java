package org.dromara.budget.service;

import org.dromara.budget.domain.vo.BudgetTemplateItemVo;
import org.dromara.budget.domain.bo.BudgetTemplateItemBo;
import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 预算模板科目Service接口
 *
 * @author Lion Li
 * @date 2026-08-29
 */
public interface IBudgetTemplateItemService {

    /**
     * 查询预算模板科目
     *
     * @param id 主键
     * @return 预算模板科目
     */
    BudgetTemplateItemVo queryById(Long id);

    /**
     * 分页查询预算模板科目列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 预算模板科目分页列表
     */
    TableDataInfo<BudgetTemplateItemVo> queryPageList(BudgetTemplateItemBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的预算模板科目列表
     *
     * @param bo 查询条件
     * @return 预算模板科目列表
     */
    List<BudgetTemplateItemVo> queryList(BudgetTemplateItemBo bo);

    /**
     * 分页查询某方案的已停用科目（del_flag=1），用于还原管理
     *
     * @param planId       方案ID
     * @param templateCode 预算表编号(null 查全部)
     * @param pageQuery    分页参数
     * @return 已停用科目分页
     */
    TableDataInfo<BudgetTemplateItemVo> queryDisabledPage(Long planId, String templateCode, PageQuery pageQuery);

    /**
     * 还原已停用科目（del_flag 1 -> 0）
     *
     * @param ids 科目主键集合
     * @return 是否还原成功
     */
    Boolean restoreByIds(Collection<Long> ids);

    /**
     * 解析某方案在某模板下的科目集（方案级隔离）：
     * 优先返回 plan_id = planId 的科目快照；若该方案尚未初始化学科，则回退基础模板(plan_id=0)。
     *
     * @param planId       方案ID（null 时按基础模板 plan_id=0）
     * @param templateCode 预算表编号（null 查全部）
     * @return 科目列表（按 templateCode、itemOrder 排序）
     */
    List<BudgetTemplateItem> listByPlanAndTemplate(Long planId, String templateCode);

    /**
     * 统计某方案的科目快照行数，用于判断是否已完成科目初始化
     *
     * @param planId 方案ID
     * @return plan_id = planId 的行数
     */
    long countByPlan(Long planId);

    /**
     * 查询参与填报的公司列表，用于科目"适用公司"范围选择
     *
     * @return [ {deptId, deptName} ]
     */
    List<Map<String, Object>> listCompanies();

    /**
     * 从科目主数据库导入科目到指定方案（复制挂接，自动补全缺失祖先链，已存在则跳过）
     *
     * @param planId     目标方案ID
     * @param subjectIds 选中的科目主数据ID集合
     * @return 本次实际新增的方案科目数量
     */
    int importFromMaster(Long planId, Collection<Long> subjectIds);

    /**
     * 新增预算模板科目
     *
     * @param bo 预算模板科目
     * @return 是否新增成功
     */
    Boolean insertByBo(BudgetTemplateItemBo bo);

    /**
     * 修改预算模板科目
     *
     * @param bo 预算模板科目
     * @return 是否修改成功
     */
    Boolean updateByBo(BudgetTemplateItemBo bo);

    /**
     * 校验并批量删除预算模板科目信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
