package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 预算表字段可见性规则对象 budget_field_visibility
 *
 * <p>按子公司(org_id)控制"某预算表/方案/年度下某填报字段(科目主数据)"的可见/填报权限。
 * 仅作用于填报端显隐，<b>不影响集团层面汇总计算</b>（汇总仍按科目聚，忽略本表）。
 *
 * @author Lion Li
 * @date 2026-09-28
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_field_visibility")
public class BudgetFieldVisibility extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
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
    private Long refSubjectId;

    /**
     * 目标子公司ID
     */
    private Long orgId;

    /**
     * 权限:HIDE=隐藏/READONLY=只读/EDIT=可填报
     */
    private String fieldPermission;

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
