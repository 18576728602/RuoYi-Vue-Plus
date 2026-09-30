package org.dromara.budget.service;

import org.dromara.budget.domain.bo.AdjustmentApproveBo;
import org.dromara.budget.domain.vo.BudgetAdjustmentVo;
import org.dromara.budget.domain.vo.BudgetAdjustmentImpactVo;
import org.dromara.budget.domain.bo.BudgetAdjustmentBo;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;

/**
 * 预算调整记录Service接口
 *
 * @author Lion Li
 * @date 2026-08-29
 */
public interface IBudgetAdjustmentService {

    /**
     * 查询预算调整记录
     *
     * @param id 主键
     * @return 预算调整记录
     */
    BudgetAdjustmentVo queryById(Long id);

    /**
     * 分页查询预算调整记录列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 预算调整记录分页列表
     */
    TableDataInfo<BudgetAdjustmentVo> queryPageList(BudgetAdjustmentBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的预算调整记录列表
     *
     * @param bo 查询条件
     * @return 预算调整记录列表
     */
    List<BudgetAdjustmentVo> queryList(BudgetAdjustmentBo bo);

    /**
     * 新增预算调整记录
     *
     * @param bo 预算调整记录
     * @return 是否新增成功
     */
    Boolean insertByBo(BudgetAdjustmentBo bo);

    /**
     * 修改预算调整记录
     *
     * @param bo 预算调整记录
     * @return 是否修改成功
     */
    Boolean updateByBo(BudgetAdjustmentBo bo);

    /**
     * 校验并批量删除预算调整记录信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    /**
     * 审批预算调整申请
     */
    Boolean approve(AdjustmentApproveBo bo);

    /**
     * 查询某单位某方案下已审批通过的预算表编号列表（用于预算调整申请时，科目/预算表下拉仅展示本单位可调整的表）
     *
     * @param planId 预算方案ID
     * @param orgId  填报单位部门ID（集团账号可指定；子公司账号强制取本单位）
     * @return 已审批通过的预算表编号集合
     */
    List<String> listApprovedTemplateCodes(Long planId, Long orgId);

    /**
     * 调整影响分析：预估本次调整对本公司(本表)及集团(同科目)的影响，并追溯该科目历史调整情况。
     *
     * @param planId        预算方案ID
     * @param orgId         调整单位(本公司)
     * @param templateCode  预算表编号
     * @param itemCode      调整科目编码
     * @param originalAmount 调整前预算(万元)
     * @param adjustAmount  调整金额(万元,正增负减)
     * @return 影响分析结果
     */
    BudgetAdjustmentImpactVo analyzeImpact(Long planId, Long orgId, String templateCode,
                                           String itemCode, java.math.BigDecimal originalAmount,
                                           java.math.BigDecimal adjustAmount);

}
