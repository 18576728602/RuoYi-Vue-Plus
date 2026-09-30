package org.dromara.budget.domain.vo;

import org.dromara.budget.domain.BudgetSubjectMaster;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 预算科目主数据视图对象 budget_subject_master
 *
 * @author Lion Li
 * @date 2026-09-15
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BudgetSubjectMaster.class)
public class BudgetSubjectMasterVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @ExcelProperty(value = "主键ID")
    private Long id;

    /**
     * 科目编码
     */
    @ExcelProperty(value = "科目编码")
    private String subjectCode;

    /**
     * 科目名称
     */
    @ExcelProperty(value = "科目名称")
    private String subjectName;

    /**
     * 父级科目编码
     */
    @ExcelProperty(value = "父级科目编码")
    private String parentCode;

    /**
     * 科目层级
     */
    @ExcelProperty(value = "科目层级")
    private Integer level;

    /**
     * 同级排序
     */
    @ExcelProperty(value = "同级排序")
    private Integer sort;

    /**
     * 科目类型:SYS=集团共享/DEPT=公司私有
     */
    @ExcelProperty(value = "科目类型")
    private String subjectType;

    /**
     * 归属公司ID
     */
    private Long orgId;

    /**
     * 适用公司ID(逗号分隔,空=全部公司)
     */
    private String orgScope;

    /**
     * 默认所属预算表
     */
    private String templateCode;

    /**
     * 预算年度
     */
    private Integer budgetYear;

    /**
     * 所属预算表模板ID(硬关联)
     */
    private Long templateId;

    /**
     * 有效标记
     */
    private String validFlag;

    /**
     * 是否汇总行:1=汇总/大类,0=明细
     */
    @ExcelProperty(value = "是否汇总行")
    private Integer isSummary;

    /**
     * 是否可编辑:1=可编辑,0=只读
     */
    @ExcelProperty(value = "是否可编辑")
    private Integer isEditable;

    /**
     * 行类型:HEAD分组/ITEM明细/SUM汇总/REF只读基准
     */
    @ExcelProperty(value = "行类型")
    private String rowType;

    /**
     * 业务分类编码(引用 budget_category.category_code,如 01=公司基本信息/02=营业收入;空=未分类)
     */
    @ExcelProperty(value = "业务分类编码")
    private String categoryCode;

    /**
     * 数据类型:CURRENCY=货币金额/NUMBER=普通数字/PERCENT=百分比/TEXT=文本
     */
    @ExcelProperty(value = "数据类型")
    private String dataType;

    /**
     * 计量单位(如 元/万元/人/人次/天/%)
     */
    @ExcelProperty(value = "计量单位")
    private String unit;

    /**
     * 小数位:CURRENCY/PERCENT 可配(0-6,默认2),NUMBER 固定0(整数),TEXT 不适用
     */
    @ExcelProperty(value = "小数位")
    private Integer decimalPlaces;

    /**
     * 是否参与汇总:true=参与/false=不参与
     */
    @ExcelProperty(value = "是否参与汇总")
    private Boolean participateSummary;

    /**
     * 是否公式计算项:true=公式计算/false=手工填报
     */
    @ExcelProperty(value = "计算项")
    private Boolean isFormula;

    /**
     * 是否必填:true=必填/false=选填
     */
    @ExcelProperty(value = "是否必填")
    private Boolean requiredFlag;

    /**
     * 备注
     */
    @ExcelProperty(value = "备注")
    private String remark;

    /**
     * 挂接引用数(同 budget_template_item.ref_subject_id 行数, 含方案挂接 + 预算表挂载)
     */
    private Long refCount;

    /**
     * 创建时间
     */
    @ExcelProperty(value = "创建时间")
    private Date createTime;

    /**
     * 创建人
     */
    @ExcelProperty(value = "创建人")
    private Long createBy;

    /**
     * 更新时间
     */
    @ExcelProperty(value = "更新时间")
    private Date updateTime;

    /**
     * 更新人
     */
    @ExcelProperty(value = "更新人")
    private Long updateBy;

}