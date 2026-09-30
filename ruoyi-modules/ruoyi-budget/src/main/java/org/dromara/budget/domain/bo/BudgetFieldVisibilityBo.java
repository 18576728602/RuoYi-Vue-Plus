package org.dromara.budget.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.budget.domain.BudgetFieldVisibility;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 预算表字段可见性规则业务对象 budget_field_visibility
 *
 * @author Lion Li
 * @date 2026-09-28
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BudgetFieldVisibility.class, reverseConvertGenerate = false)
public class BudgetFieldVisibilityBo extends BaseEntity {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 预算表ID(空=对全部预算表适用)
     */
    private Long templateId;

    /**
     * 预算方案ID(0=对全部方案生效)
     */
    private Long planId;

    /**
     * 预算年度(空=跨年度)
     */
    private Integer budgetYear;

    /**
     * 科目主数据ID(即"填报字段")
     */
    @NotNull(message = "科目(字段)不能为空")
    private Long refSubjectId;

    /**
     * 目标子公司ID
     */
    @NotNull(message = "目标子公司不能为空")
    private Long orgId;

    /**
     * 权限:HIDE=隐藏/READONLY=只读/EDIT=可填报
     */
    @NotBlank(message = "字段权限不能为空")
    private String fieldPermission;

    /**
     * 备注
     */
    private String remark;

}
