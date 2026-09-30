package org.dromara.budget.domain.bo;

import org.dromara.budget.domain.BudgetPlan;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 预算方案业务对象 budget_plan
 *
 * @author Lion Li
 * @date 2026-08-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BudgetPlan.class, reverseConvertGenerate = false)
public class BudgetPlanBo extends BaseEntity {

    /**
     * 主键ID
     */
    @NotNull(message = "主键ID不能为空", groups = { EditGroup.class })
    private Long id;

    /**
     * 方案编码
     */
    @NotBlank(message = "方案编码不能为空", groups = { AddGroup.class, EditGroup.class })
    private String planCode;

    /**
     * 方案名称
     */
    @NotBlank(message = "方案名称不能为空", groups = { AddGroup.class, EditGroup.class })
    private String planName;

    /**
     * 预算年度
     */
    @NotNull(message = "预算年度不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long budgetYear;

    /**
     * 填报开始日期
     */
    @NotNull(message = "填报开始日期不能为空", groups = { AddGroup.class, EditGroup.class })
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startDate;

    /**
     * 填报截止日期
     */
    @NotNull(message = "填报截止日期不能为空", groups = { AddGroup.class, EditGroup.class })
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endDate;

    /**
     * 状态: DRAFT=草稿, PUBLISHED=已发布, CLOSED=已关闭, ARCHIVED=已归档
     */
    @NotBlank(message = "状态: DRAFT=草稿, PUBLISHED=已发布, CLOSED=已关闭, ARCHIVED=已归档不能为空", groups = { AddGroup.class, EditGroup.class })
    private String status;

    /**
     * 审批流程JSON配置
     */
    private String approvalFlow;

    /**
     * 填报公司范围: 逗号分隔公司ID, 空=全部公司
     */
    private String orgScope;

    /**
     * 方案说明
     */
    private String description;


}
