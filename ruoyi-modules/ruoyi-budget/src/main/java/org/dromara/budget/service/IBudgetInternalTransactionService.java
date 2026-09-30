package org.dromara.budget.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;

import java.util.List;
import java.util.Map;

/**
 * 内部交易登记 Service接口
 */
public interface IBudgetInternalTransactionService {

    /**
     * 分页查询内部交易列表
     */
    TableDataInfo<Map<String, Object>> queryList(Long planId, String transactionType, String status, PageQuery pageQuery);

    /**
     * 新增内部交易
     */
    Boolean insert(Map<String, Object> params);

    /**
     * 修改内部交易
     */
    Boolean update(Map<String, Object> params);

    /**
     * 删除内部交易
     */
    Boolean deleteById(Long id);

    /**
     * 批量删除
     */
    Boolean deleteByIds(List<Long> ids);

    /**
     * 确认内部交易（DRAFT → CONFIRMED）
     */
    Boolean confirm(List<Long> ids);

    /**
     * 撤销确认（CONFIRMED → DRAFT）
     */
    Boolean revokeConfirm(List<Long> ids);

    /**
     * 获取抵销项目配置（按交易类型）
     */
    List<Map<String, Object>> getEliminationConfigs(String transactionType);

    /**
     * 获取部门列表（仅子公司级别）
     */
    List<Map<String, Object>> getDeptOptions();
}
