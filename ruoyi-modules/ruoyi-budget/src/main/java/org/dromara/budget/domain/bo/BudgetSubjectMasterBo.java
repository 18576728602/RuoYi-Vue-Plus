package org.dromara.budget.domain.bo;

import org.dromara.budget.domain.BudgetSubjectMaster;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 预算科目主数据业务对象 budget_subject_master
 *
 * @author Lion Li
 * @date 2026-09-15
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BudgetSubjectMaster.class, reverseConvertGenerate = false)
public class BudgetSubjectMasterBo extends BaseEntity {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 科目编码(SYS全集团唯一/DEPT公司内唯一)
     */
    @NotBlank(message = "科目编码不能为空", groups = { AddGroup.class, EditGroup.class })
    private String subjectCode;

    /**
     * 科目名称
     */
    @NotBlank(message = "科目名称不能为空", groups = { AddGroup.class, EditGroup.class })
    private String subjectName;

    /**
     * 父级科目编码(顶层为空)
     */
    private String parentCode;

    /**
     * 科目层级(自动由父级推导,默认1)
     */
    private Integer level;

    /**
     * 同级排序
     */
    private Integer sort;

    /**
     * 科目类型:SYS=集团共享/DEPT=公司私有
     */
    @NotBlank(message = "科目类型不能为空", groups = { AddGroup.class })
    private String subjectType;

    /**
     * 归属公司ID(DEPT时必填,SYS为空)
     */
    private Long orgId;

    /**
     * 适用公司ID(逗号分隔,空=全部公司)
     */
    private String orgScope;

    /**
     * 默认所属预算表(01-16,便于挂接定位)
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
     * 有效标记:0=停用,1=有效
     */
    private String validFlag;

    /**
     * 行类型:HEAD分组/ITEM明细/SUM汇总/REF只读基准(空=新建默认ITEM,有子级自动HEAD)
     */
    private String rowType;

    /**
     * 业务分类编码(引用 budget_category.category_code,如 01=公司基本信息/02=营业收入;用于筛选)
     */
    private String categoryCode;
    /**
     * 数据类型:CURRENCY=货币金额/NUMBER=普通数字/PERCENT=百分比/TEXT=文本
     */
    private String dataType;

    /**
     * 计量单位(如 元/万元/人/人次/天/%)
     */
    private String unit;

    /**
     * 小数位:CURRENCY/PERCENT 可配(0-6,默认2),NUMBER 固定0(整数),TEXT 不适用
     */
    private Integer decimalPlaces;

    /**
     * 是否参与汇总:true=参与/false=不参与
     */
    private Boolean participateSummary;

    /**
     * 是否公式计算项:true=公式计算/false=手工填报
     */
    private Boolean isFormula;

    /**
     * 是否必填:true=必填/false=选填
     */
    private Boolean requiredFlag;
    /**
     * 备注
     */
    private String remark;
}