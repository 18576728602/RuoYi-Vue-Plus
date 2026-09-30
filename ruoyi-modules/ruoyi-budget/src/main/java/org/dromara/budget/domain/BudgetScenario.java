package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 情景模拟参数表  budget_scenario
 *
 * @author Lion Li
 * @date 2026-09-15
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_scenario")
public class BudgetScenario extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /** 预算方案ID */
    private Long planId;

    /** 情景类型:BASE基准/OPTIMISTIC乐观/PESSIMISTIC悲观/CUSTOM自定义 */
    private String scenarioType;

    /** 情景名称 */
    private String scenarioName;

    /** 收入增长率(%) */
    private BigDecimal revenueGrowth;

    /** 毛利率(%) */
    private BigDecimal grossMargin;

    /** 成本费用率(%) */
    private BigDecimal costExpenseRate;

    /** 融资成本/利率(%) */
    private BigDecimal financingRate;

    /** 投资回报率(%) */
    private BigDecimal investReturnRate;

    /** 是否默认情景 */
    private Integer isDefault;

    /** 说明 */
    private String remark;
}