package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 预算操作日志对象 budget_operation_log
 *
 * <p>记录预算填报与审批的关键动作（保存草稿/提交/审批通过/驳回），
 * 作为"最新动态"时间线的数据来源，保证每次操作都有真实时刻日志。</p>
 *
 * @author Lion Li
 * @date 2026-09-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_operation_log")
public class BudgetOperationLog extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 预算方案ID
     */
    private Long planId;

    /**
     * 预算方案名称（动作发生时固化，防日后改名导致追溯失真）
     */
    private String planName;

    /**
     * 填报单位ID
     */
    private Long deptId;

    /**
     * 预算表编号
     */
    private String templateCode;

    /**
     * 预算表名称（动作发生时固化）
     */
    private String templateName;

    /**
     * 动作类型 SAVE_DRAFT/SUBMIT/APPROVE/REJECT
     */
    private String actionType;

    /**
     * 动作名称（保存草稿/提交审批/审批通过/审批驳回）
     */
    private String actionLabel;

    /**
     * 对象类型 FILL/ADJUSTMENT
     */
    private String targetType;

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 操作人
     */
    private String operatorName;

    /**
     * 备注（驳回原因等）
     */
    private String remark;
}