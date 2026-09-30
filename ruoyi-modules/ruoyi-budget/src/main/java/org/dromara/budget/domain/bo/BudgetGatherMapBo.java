package org.dromara.budget.domain.bo;

import org.dromara.budget.domain.BudgetGatherMap;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;

/**
 * 预算科目归集映射业务对象 budget_gather_map
 *
 * @author Lion Li
 * @date 2026-09-16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BudgetGatherMap.class, reverseConvertGenerate = false)
public class BudgetGatherMapBo extends BaseEntity {

    /**
     * 主键ID
     */
    @NotNull(message = "主键ID不能为空", groups = { EditGroup.class })
    private Long id;

    /**
     * 方案ID: 0=基础/默认
     */
    private Long planId;

    /**
     * 源部门ID(本部内设部门)
     */
    @NotNull(message = "源部门不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long deptId;

    /**
     * 源部门名称
     */
    private String deptName;

    /**
     * 源预算表编号(通常为17)
     */
    private String srcTemplateCode;

    /**
     * 源科目编码(17表明细)
     */
    @NotBlank(message = "源科目编码不能为空", groups = { AddGroup.class, EditGroup.class })
    private String srcItemCode;

    /**
     * 源科目名称
     */
    private String srcItemName;

    /**
     * 目标预算表编号(通常为06)
     */
    private String tgtTemplateCode;

    /**
     * 目标部门ID(NULL/空=本部)
     */
    private Long tgtDeptId;

    /**
     * 目标部门名称
     */
    private String tgtDeptName;

    /**
     * 目标科目编码(06表汇总科目)
     */
    @NotBlank(message = "目标科目编码不能为空", groups = { AddGroup.class, EditGroup.class })
    private String tgtItemCode;

    /**
     * 目标科目名称
     */
    private String tgtItemName;
}