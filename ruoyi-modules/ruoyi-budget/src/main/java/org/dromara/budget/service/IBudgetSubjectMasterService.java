package org.dromara.budget.service;

import org.dromara.budget.domain.bo.BudgetSubjectMasterBo;
import org.dromara.budget.domain.vo.BudgetSubjectMasterVo;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;

import java.util.Collection;
import java.util.List;

/**
 * 预算科目主数据Service接口
 *
 * @author Lion Li
 * @date 2026-09-15
 */
public interface IBudgetSubjectMasterService {

    /**
     * 查询科目主数据
     *
     * @param id 主键
     * @return 科目主数据
     */
    BudgetSubjectMasterVo queryById(Long id);

    /**
     * 分页查询科目主数据列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 科目主数据分页列表
     */
    TableDataInfo<BudgetSubjectMasterVo> queryPageList(BudgetSubjectMasterBo bo, PageQuery pageQuery);

    /**
     * 查询科目主数据列表(全量,按层级排序,用于构造树)
     *
     * @param bo 查询条件
     * @return 科目主数据列表
     */
    List<BudgetSubjectMasterVo> queryList(BudgetSubjectMasterBo bo);

    /**
     * 自动生成下级科目编码(区别于手输,保证唯一且层级规整)
     *
     * @param templateCode 所属预算表编码
     * @param parentCode   父级科目编码(空=生成一级科目编码)
     * @param subjectType  科目类型
     * @param orgId        归属公司ID
     * @return 生成的科目编码
     */
    String nextCode(String templateCode, String parentCode, String subjectType, Long orgId);

    /**
     * 新增科目主数据
     *
     * @param bo 科目主数据
     * @return 是否成功
     */
    Boolean insertByBo(BudgetSubjectMasterBo bo);

    /**
     * 修改科目主数据
     *
     * @param bo 科目主数据
     * @return 是否成功
     */
    Boolean updateByBo(BudgetSubjectMasterBo bo);

    /**
     * 批量删除科目主数据
     *
     * @param ids     主键集合
     * @param isValid 是否校验存在子科目
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    /**
     * 启用/停用科目
     *
     * @param id        科目ID
     * @param validFlag 有效标记:0=停用,1=有效
     * @return 是否成功
     */
    Boolean changeValid(Long id, String validFlag);

    /**
     * 统计该主数据科目被方案/预算表挂接引用的次数
     *
     * @param id 科目ID
     * @return 引用次数
     */
    Long countRefs(Long id);
}