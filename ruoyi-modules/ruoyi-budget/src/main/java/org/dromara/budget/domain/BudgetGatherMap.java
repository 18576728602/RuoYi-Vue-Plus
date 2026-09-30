package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 预算科目归集映射对象 budget_gather_map
 *
 * 将「17表-集团本部内设部门费用」的部门个性化明细，按部门+科目一对一归集到「06表-管理费用」的目标科目。
 *
 * @author Lion Li
 * @date 2026-09-16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_gather_map")
public class BudgetGatherMap extends TenantEntity {

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
     * 源部门ID(本部内设部门)
     */
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
    private String tgtItemCode;

    /**
     * 目标科目名称
     */
    private String tgtItemName;

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