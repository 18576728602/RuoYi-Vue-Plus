package org.dromara.budget.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 预算目标下达 保存草稿 Bo
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
public class BudgetTargetBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 预算方案ID
     */
    @NotNull(message = "预算方案不能为空")
    private Long planId;

    /**
     * 目标公司ID
     */
    @NotNull(message = "目标公司不能为空")
    private Long deptId;

    /**
     * 目标科目明细
     */
    @Valid
    @NotNull(message = "目标科目不能为空")
    private List<Item> items;

    @Data
    public static class Item implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String templateCode;

        private String itemCode;

        private String itemName;

        private BigDecimal targetAmount;

        private BigDecimal toleranceMin;

        private BigDecimal toleranceMax;

        private Integer itemOrder;
    }
}