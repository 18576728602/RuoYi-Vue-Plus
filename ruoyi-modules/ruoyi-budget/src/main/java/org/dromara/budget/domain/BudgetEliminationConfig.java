package org.dromara.budget.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 抵销项目配置
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_elimination_config")
public class BudgetEliminationConfig extends BaseEntity {

    @TableId(value = "id")
    private Long id;

    private String projectCode;

    private String projectName;

    private String transactionType;

    private String templateCode;

    private String matchKeyword;

    private Integer sortNum;

    private String tenantId;
}
