package org.dromara.budget.service;

import org.dromara.budget.domain.vo.DeptBudgetModuleVo;
import org.dromara.budget.domain.bo.DeptBudgetModuleBo;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;

/**
 * 部门-预算板块映射Service接口
 *
 * @author Lion Li
 * @date 2026-08-29
 */
public interface IDeptBudgetModuleService {

    /**
     * 查询部门-预算板块映射
     *
     * @param id 主键
     * @return 部门-预算板块映射
     */
    DeptBudgetModuleVo queryById(Long id);

    /**
     * 分页查询部门-预算板块映射列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 部门-预算板块映射分页列表
     */
    TableDataInfo<DeptBudgetModuleVo> queryPageList(DeptBudgetModuleBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的部门-预算板块映射列表
     *
     * @param bo 查询条件
     * @return 部门-预算板块映射列表
     */
    List<DeptBudgetModuleVo> queryList(DeptBudgetModuleBo bo);

    /**
     * 新增部门-预算板块映射
     *
     * @param bo 部门-预算板块映射
     * @return 是否新增成功
     */
    Boolean insertByBo(DeptBudgetModuleBo bo);

    /**
     * 修改部门-预算板块映射
     *
     * @param bo 部门-预算板块映射
     * @return 是否修改成功
     */
    Boolean updateByBo(DeptBudgetModuleBo bo);

    /**
     * 校验并批量删除部门-预算板块映射信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
