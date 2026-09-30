package org.dromara.budget.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import org.dromara.budget.domain.BudgetTarget;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算目标下达视图对象
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BudgetTarget.class)
public class BudgetTargetVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long planId;

    private String templateCode;

    private Long deptId;

    private String itemCode;

    private String itemName;

    private BigDecimal targetAmount;

    private BigDecimal toleranceMin;

    private BigDecimal toleranceMax;

    private Integer itemOrder;

    private Integer versionNo;

    private String status;
}