package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serial;

/**
 * 预算方案对象 budget_plan
 *
 * @author Lion Li
 * @date 2026-08-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_plan")
public class BudgetPlan extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 方案编码
     */
    private String planCode;

    /**
     * 方案名称
     */
    private String planName;

    /**
     * 预算年度
     */
    private Long budgetYear;

    /**
     * 填报开始日期
     */
    private Date startDate;

    /**
     * 填报截止日期
     */
    private Date endDate;

    /**
     * 状态: DRAFT=草稿, PUBLISHED=已发布, CLOSED=已关闭, ARCHIVED=已归档
     */
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
