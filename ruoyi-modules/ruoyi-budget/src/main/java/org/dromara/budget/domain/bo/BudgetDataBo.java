package org.dromara.budget.domain.bo;

import org.dromara.budget.domain.BudgetData;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * 预算填报数据业务对象 budget_data
 *
 * @author Lion Li
 * @date 2026-08-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BudgetData.class, reverseConvertGenerate = false)
public class BudgetDataBo extends BaseEntity {

    /**
     * 主键ID
     */
    @NotNull(message = "主键ID不能为空", groups = { EditGroup.class })
    private Long id;

    /**
     * 预算方案ID
     */
    @NotNull(message = "预算方案ID不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long planId;

    /**
     * 组织ID(单位)
     */
    @NotNull(message = "组织ID(单位)不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long orgId;

    /**
     * 部门ID(集团本部部门填报时)
     */
    private Long deptId;

    /**
     * 预算表编号
     */
    @NotBlank(message = "预算表编号不能为空", groups = { AddGroup.class, EditGroup.class })
    private String templateCode;

    /**
     * 科目编码
     */
    @NotBlank(message = "科目编码不能为空", groups = { AddGroup.class, EditGroup.class })
    private String itemCode;

    /**
     * 预算金额
     */
    private BigDecimal budgetAmount;

    /**
     * 上年实际金额
     */
    private BigDecimal lastActual;

    /**
     * 实际执行金额
     */
    private BigDecimal executionAmount;

    /**
     * 数据版本: BUDGET=预算数, ACTUAL=上年实际, EXECUTION=执行数
     */
    private String dataVersion;

    /**
     * 填报说明
     */
    private String remark;

    /**
     * 状态: DRAFT=草稿, SUBMITTED=已提交, APPROVED=已审批, REJECTED=已驳回
     */
    private String status;


}