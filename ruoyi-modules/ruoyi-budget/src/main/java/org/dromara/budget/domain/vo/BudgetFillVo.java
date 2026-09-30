package org.dromara.budget.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.budget.domain.BudgetData;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算填报数据视图对象（前端表格行数据）
 */
@Data
public class BudgetFillVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 填报数据ID（已有数据时返回，新增时为null）
     */
    private Long id;

    /**
     * 预算方案ID
     */
    private Long planId;

    /**
     * 组织ID
     */
    private Long orgId;

    /**
     * 部门ID
     */
    private Long deptId;

    /**
     * 预算表编号
     */
    private String templateCode;

    /**
     * 预算表ID（挂载行来源，用于字段可见性规则解析 template_id 维度）
     */
    private Long templateId;

    /**
     * 科目主数据ID（挂载行 ref_subject_id，字段可见性规则按此关联；空=无主数据引用）
     */
    private Long refSubjectId;

    /**
     * 预算表名称
     */
    private String templateName;

    /**
     * 科目编码
     */
    private String itemCode;

    /**
     * 科目名称
     */
    private String itemName;

    /**
     * 上级科目编码
     */
    private String parentCode;

    /**
     * 层级
     */
    private Long itemLevel;

    /**
     * 排序号
     */
    private Long itemOrder;

    /**
     * 是否汇总行: 0=否, 1=是
     */
    private Long isSummary;

    /**
     * 是否可编辑: 0=否, 1=是
     */
    private Long isEditable;

    /**
     * 计算公式(如: SUM(1601,1602,1603) 或 SUM(children))
     */
    private String formula;

    /**
     * 上年实际金额
     */
    private BigDecimal lastActual;

    /**
     * 预算金额
     */
    private BigDecimal budgetAmount;

    /**
     * 实际执行金额
     */
    private BigDecimal executionAmount;

    /**
     * 填报说明
     */
    private String remark;

    /**
     * 状态: DRAFT=草稿, SUBMITTED=已提交, APPROVED=已审批, REJECTED=已驳回
     */
    private String status;

}
