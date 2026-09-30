package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 部门-预算板块映射对象 dept_budget_module
 *
 * @author Lion Li
 * @date 2026-08-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dept_budget_module")
public class DeptBudgetModule extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 方案ID: 0=基础/默认
     */
    private Long planId;

    /**
     * 部门ID
     */
    private Long deptId;

    /**
     * 预算表编号
     */
    private String templateCode;

    /**
     * 负责科目编码列表(逗号分隔)
     */
    private String itemCodes;

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
