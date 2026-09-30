package org.dromara.budget.domain.bo;

import org.dromara.budget.domain.DeptBudgetModule;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;

/**
 * 部门-预算板块映射业务对象 dept_budget_module
 *
 * @author Lion Li
 * @date 2026-08-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = DeptBudgetModule.class, reverseConvertGenerate = false)
public class DeptBudgetModuleBo extends BaseEntity {

    /**
     * 主键ID
     */
    @NotNull(message = "主键ID不能为空", groups = { EditGroup.class })
    private Long id;

    /**
     * 部门ID
     */
    @NotNull(message = "部门ID不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long deptId;

    /**
     * 预算表编号
     */
    @NotBlank(message = "预算表编号不能为空", groups = { AddGroup.class, EditGroup.class })
    private String templateCode;

    /**
     * 负责科目编码列表(逗号分隔)
     */
    private String itemCodes;


}
