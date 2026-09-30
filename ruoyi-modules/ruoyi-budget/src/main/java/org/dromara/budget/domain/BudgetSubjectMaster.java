package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 预算科目主数据对象 budget_subject_master
 *
 * <p>引用式科目建模的唯一字典。SYS=集团共享主数据(全集团引用,集团账号维护)；
 * DEPT=公司私有科目(org_id 指向归属公司,仅该公司可见)。
 *
 * @author Lion Li
 * @date 2026-09-15
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_subject_master")
public class BudgetSubjectMaster extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 科目编码(SYS全集团唯一/DEPT公司内唯一)
     */
    private String subjectCode;

    /**
     * 科目名称
     */
    private String subjectName;

    /**
     * 父级科目编码(顶层为空)
     */
    private String parentCode;

    /**
     * 科目层级
     */
    private Integer level;

    /**
     * 同级排序
     */
    private Integer sort;

    /**
     * 科目类型:SYS=集团共享/DEPT=公司私有
     */
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
     * 预算年度(同一编码不同年份的科目副本所属年度)
     */
    private Integer budgetYear;

    /**
     * 所属预算表模板ID(硬关联 budget_template.id，方案A每表独立模板树)
     */
    private Long templateId;

    /**
     * 有效标记:0=停用,1=有效
     */
    private String validFlag;

    /**
     * 是否汇总行:1=汇总/大类(子级累加),0=明细(可填报)
     */
    private Integer isSummary;

    /**
     * 是否可编辑:1=可编辑,0=只读(汇总行/计算行明显为0)
     */
    private Integer isEditable;

    /**
     * 行类型:HEAD=分组/表头, ITEM=明细(可填报), SUM=汇总(子级累加), REF=只读基准(上一年实际等)
     */
    private String rowType;

    /**
     * 业务分类编码(引用 budget_category.category_code,如 01=公司基本信息/02=营业收入;空=未分类)
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
     * 小数位:CURRENCY/PERCENT 可配(0-6,默认2),NUMBER 固定0(整数,人数类禁小数),TEXT 不适用
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

    /**
     * 乐观锁版本号
     */
    @Version
    private Long version;

    /**
     * 删除标志:0=正常,1=删除
     */
    @TableLogic
    private Long delFlag;

}