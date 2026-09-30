package org.dromara.budget.domain.vo;

import org.dromara.budget.domain.BudgetOperationLog;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 预算操作日志视图对象 budget_operation_log
 *
 * @author Lion Li
 * @date 2026-09-09
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BudgetOperationLog.class)
public class BudgetOperationLogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 预算方案ID
     */
    private Long planId;

    /**
     * 预算方案名称
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
     * 预算表名称
     */
    private String templateName;

    /**
     * 动作类型
     */
    private String actionType;

    /**
     * 动作名称
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
     * 操作时间
     */
    private Date createTime;
}