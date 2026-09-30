package org.dromara.budget.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.math.BigDecimal;

/**
 * 合并报表手动抵销调整
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_consolidation_adjustment")
public class BudgetConsolidationAdjustment extends BaseEntity {

    @TableId(value = "id")
    private Long id;

    /**
     * 预算方案ID
     */
    private Long planId;

    /**
     * 报表类型: IS-利润表, BS-资产负债表, CF-现金流量表
     */
    private String statementType;

    /**
     * 科目编码
     */
    private String itemCode;

    /**
     * 手动抵销调整金额(万元)
     */
    private BigDecimal adjustAmount;

    private String tenantId;
}