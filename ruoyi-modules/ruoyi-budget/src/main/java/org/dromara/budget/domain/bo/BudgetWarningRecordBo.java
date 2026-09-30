package org.dromara.budget.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 智能预警闭环处理 业务对象
 *
 * @author Lion Li
 * @date 2026-09-15
 */
@Data
public class BudgetWarningRecordBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 记录主键(处置/升级/解除时必填)
     */
    private Long recordId;

    /**
     * 新建时可选的预警定位字段
     */
    private String planId;

    /**
     * 单位ID
     */
    private Long orgId;

    /**
     * 预算表编号
     */
    private String templateCode;

    /**
     * 科目编码
     */
    private String itemCode;

    /**
     * 预警等级: RED/ORANGE/YELLOW
     */
    private String level;

    /**
     * 多源预警标识
     */
    private String sources;

    /**
     * 综合风险评分
     */
    private String riskScore;

    /**
     * 执行率(%)
     */
    private String execRate;

    /**
     * 处置目标状态: PROCESSING=处置中, CONFIRMED=已确认, RESOLVED=已整改, CLOSED=已解除
     */
    private String status;

    /**
     * 处置人姓名
     */
    private String handlerName;

    /**
     * 整改措施
     */
    private String improvePlan;

    /**
     * 跟踪结果
     */
    private String trackResult;

    /**
     * 是否升级: 0=否, 1=是
     */
    private Integer escalated;

    /**
     * 升级对象
     */
    private String escalateTo;

    /**
     * 备注
     */
    private String remark;
}