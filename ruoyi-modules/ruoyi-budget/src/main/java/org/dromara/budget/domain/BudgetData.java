package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 预算填报数据对象 budget_data
 *
 * @author Lion Li
 * @date 2026-08-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_data")
public class BudgetData extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 预算方案ID
     */
    private Long planId;

    /**
     * 组织ID(单位)
     */
    private Long orgId;

    /**
     * 部门ID(集团本部部门填报时)
     */
    private Long deptId;

    /**
     * 预算表编号
     */
    private String templateCode;

    /**
     * 科目编码
     */
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
     * 第一季度执行金额
     */
    private BigDecimal q1Amount;

    /**
     * 第二季度执行金额
     */
    private BigDecimal q2Amount;

    /**
     * 第三季度执行金额
     */
    private BigDecimal q3Amount;

    /**
     * 第四季度执行金额
     */
    private BigDecimal q4Amount;





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

    /**
     * 乐观锁版本号
     */
    @Version
    private Long version;

    /**
     * 删除标志: 0=正常, 其他=已删除
     */
    @TableLogic
    private Long delFlag;


}
