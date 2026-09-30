package org.dromara.budget.domain.bo;

import org.dromara.budget.domain.BudgetControlRule;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 预算控制规则业务对象 budget_control_rule
 *
 * @author Lion Li
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BudgetControlRule.class, reverseConvertGenerate = false)
public class BudgetControlRuleBo extends BaseEntity {

    /**
     * 主键ID
     */
    @NotBlank(message = "主键ID不能为空", groups = { EditGroup.class })
    private Long id;

    /**
     * 适用预算方案ID(空=全部方案)
     */
    private Long planId;

    /**
     * 适用单位(公司)ID(空=全部单位)
     */
    private Long orgId;

    /**
     * 适用预算表编号(空=全部表)
     */
    private String templateCode;

    /**
     * 控制强度:RIGID刚性/FLEXIBLE柔性/WARN预警
     */
    @NotBlank(message = "控制强度不能为空", groups = { AddGroup.class, EditGroup.class })
    private String controlStrength;

    /**
     * 提示阈值%(达到即黄预警)
     */
    private BigDecimal warnPercent;

    /**
     * 控制阈值%(达到即红/阻断)
     */
    private BigDecimal blockPercent;

    /**
     * 控制口径:ACCUM累计/CURRENT当期
     */
    private String controlScope;

    /**
     * 优先级,值越小越优先
     */
    private Integer sortOrder;

    /**
     * 是否启用:1=启用,0=停用
     */
    private Boolean enabled;

    /**
     * 备注
     */
    private String remark;
}