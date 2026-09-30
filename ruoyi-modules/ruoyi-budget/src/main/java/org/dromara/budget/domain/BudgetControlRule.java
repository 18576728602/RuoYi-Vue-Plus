package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 预算控制规则(事前拦截) 对象 budget_control_rule
 *
 * @author Lion Li
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_control_rule")
public class BudgetControlRule extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID(雪花)
     */
    @TableId(value = "id")
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

    /**
     * 删除标志:0=正常,1=删除
     */
    @TableLogic
    private Long delFlag;
}