package org.dromara.budget.service;

import org.dromara.budget.domain.bo.BudgetCategoryBo;
import org.dromara.budget.domain.vo.BudgetCategoryVo;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;

import java.util.Collection;
import java.util.List;

/**
 * 预算业务分类Service接口
 *
 * @author Lion Li
 * @date 2026-09-28
 */
public interface IBudgetCategoryService {

    /**
     * 分页查询业务分类列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分类分页列表
     */
    TableDataInfo<BudgetCategoryVo> queryPageList(BudgetCategoryBo bo, PageQuery pageQuery);

    /**
     * 查询业务分类列表(全量,用于筛选下拉与树分组)
     *
     * @param bo 查询条件
     * @return 分类列表
     */
    List<BudgetCategoryVo> queryList(BudgetCategoryBo bo);

    /**
     * 查询业务分类详细
     *
     * @param id 主键
     * @return 分类信息
     */
    BudgetCategoryVo queryById(Long id);

    /**
     * 自动生成分类编码(同级最大尾号+1,如当前01/02则返回03)
     *
     * @return 生成的分类编码
     */
    String nextCode();

    /**
     * 新增业务分类
     *
     * @param bo 分类信息
     * @return 是否成功
     */
    Boolean insertByBo(BudgetCategoryBo bo);

    /**
     * 修改业务分类
     *
     * @param bo 分类信息
     * @return 是否成功
     */
    Boolean updateByBo(BudgetCategoryBo bo);

    /**
     * 批量删除业务分类
     *
     * @param ids 主键集合
     * @return 是否成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids);
}