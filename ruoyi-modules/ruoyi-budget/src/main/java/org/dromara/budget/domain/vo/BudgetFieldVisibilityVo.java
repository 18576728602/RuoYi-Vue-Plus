package org.dromara.budget.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 预算表字段可见性规则视图对象 budget_field_visibility
 *
 * @author Lion Li
 * @date 2026-09-28
 */
@Data
@ExcelIgnoreUnannotated
public class BudgetFieldVisibilityVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 预算表ID
     */
    private Long templateId;

    /**
     * 预算表编码(冗余展示)
     */
    @ExcelProperty(value = "预算表编码")
    private String templateCode;

    /**
     * 预算表名称(冗余展示)
     */
    @ExcelProperty(value = "预算表名称")
    private String templateName;

    /**
     * 预算方案ID
     */
    private Long planId;

    /**
     * 预算年度
     */
    @ExcelProperty(value = "预算年度")
    private Integer budgetYear;

    /**
     * 科目主数据ID
     */
    private Long refSubjectId;

    /**
     * 科目编码(冗余展示)
     */
    @ExcelProperty(value = "科目编码")
    private String subjectCode;

    /**
     * 科目名称(冗余展示)
     */
    @ExcelProperty(value = "科目名称")
    private String subjectName;

    /**
     * 目标子公司ID
     */
    private Long orgId;

    /**
     * 子公司名称(冗余展示)
     */
    @ExcelProperty(value = "子公司")
    private String orgName;

    /**
     * 字段权限:HIDE=隐藏/READONLY=只读/EDIT=可填报
     */
    @ExcelProperty(value = "字段权限")
    private String fieldPermission;

    /**
     * 备注
     */
    @ExcelProperty(value = "备注")
    private String remark;

    /**
     * 创建时间
     */
    @ExcelProperty(value = "创建时间")
    private Date createTime;

    /**
     * 更新时间
     */
    @ExcelProperty(value = "更新时间")
    private Date updateTime;

}
