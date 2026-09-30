package org.dromara.budget.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 填报校验查询条件
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
public class BudgetValidateQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 预算方案ID
     */
    @NotNull(message = "预算方案ID不能为空")
    private Long planId;

    /**
     * 单位(公司/部门)ID
     */
    @NotNull(message = "填报单位不能为空")
    private Long orgId;

    /**
     * 预算表编号(空=全部表)
     */
    private String templateCode;

    /**
     * 校验类型过滤: LOGIC逻辑 / REASON 合理性 / COMPLETE 完整性(空=全部)
     */
    private String type;
}