package org.dromara.budget.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 预算版本对比查询条件
 *
 * <p>对比两个预算方案（年度版本）在相同单位/预算表/科目下的预算差异。
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
public class BudgetVersionCompareQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 对比版本A(基准方案,如下年/上年)
     */
    @NotNull(message = "对比版本A不能为空")
    private Long planIdA;

    /**
     * 对比版本B(对比方案,如本年)
     */
    @NotNull(message = "对比版本B不能为空")
    private Long planIdB;

    /**
     * 单位ID(空=全部单位)
     */
    private Long orgId;

    /**
     * 预算表编号(空=全部表)
     */
    private String templateCode;
}