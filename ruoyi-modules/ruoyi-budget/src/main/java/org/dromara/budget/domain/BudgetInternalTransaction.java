package org.dromara.budget.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 内部交易登记
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_internal_transaction")
public class BudgetInternalTransaction extends BaseEntity {

    @TableId(value = "id")
    private Long id;

    private Long planId;

    private String transactionType;

    private Long fromDeptId;

    private Long toDeptId;

    private String eliminationProject;

    private BigDecimal amount;

    private Date transactionDate;

    private String remark;

    private String status;

    private String tenantId;
}
