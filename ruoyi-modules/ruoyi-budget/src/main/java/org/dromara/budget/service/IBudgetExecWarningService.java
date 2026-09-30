package org.dromara.budget.service;

import org.dromara.budget.domain.bo.BudgetWarningRecordBo;

import java.util.List;
import java.util.Map;

/**
 * 预算执行预警 Service（智能预警增强）
 *
 * <p>在原有超预算/进度超前三级预警基础上，融合多源信号（执行、预算调整、敏感支出）
 * 计算综合风险评分，并支持预警闭环管理（触发→处置→跟踪→升级→解除）。
 */
public interface IBudgetExecWarningService {

    /**
     * 获取预算执行预警清单（多源融合）
     *
     * @param planId    预算方案ID，为空则统计全部方案
     * @param orgId     填报单位ID，为空则统计全部单位
     * @param threshold 黄色预警阈值（执行率%），为空默认80
     */
    List<Map<String, Object>> getWarningList(Long planId, Long orgId, Integer threshold);

    /**
     * 查询预警闭环处理记录
     */
    List<Map<String, Object>> listRecords(Long planId, Long orgId, String level, String status);

    /**
     * 新建预警闭环记录
     *
     * @return 新记录ID
     */
    Long createRecord(BudgetWarningRecordBo bo);

    /**
     * 处置预警（确认/整改/跟踪，留痕）
     */
    void handleRecord(BudgetWarningRecordBo bo);

    /**
     * 升级预警（提升通知对象）
     */
    void escalateRecord(BudgetWarningRecordBo bo);

    /**
     * 解除预警（关闭闭环）
     */
    void closeRecord(Long recordId, String result);
}