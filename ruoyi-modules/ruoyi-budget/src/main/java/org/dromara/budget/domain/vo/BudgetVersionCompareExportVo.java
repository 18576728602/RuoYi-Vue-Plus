package org.dromara.budget.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算版本对比导出视图对象
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
@ExcelIgnoreUnannotated
public class BudgetVersionCompareExportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 单位名称
     */
    @ExcelProperty(value = "单位名称")
    private String orgName;

    /**
     * 预算表编号
     */
    @ExcelProperty(value = "预算表编号")
    private String templateCode;

    /**
     * 预算表名称
     */
    @ExcelProperty(value = "预算表名称")
    private String templateName;

    /**
     * 科目编码
     */
    @ExcelProperty(value = "科目编码")
    private String itemCode;

    /**
     * 科目名称
     */
    @ExcelProperty(value = "科目名称")
    private String itemName;

    /**
     * 版本A预算(万元)
     */
    @ExcelProperty(value = "版本A预算(万元)")
    private BigDecimal amountA;

    /**
     * 版本B预算(万元)
     */
    @ExcelProperty(value = "版本B预算(万元)")
    private BigDecimal amountB;

    /**
     * 差异额(万元)
     */
    @ExcelProperty(value = "差异额(万元)")
    private BigDecimal diff;

    /**
     * 差异率%
     */
    @ExcelProperty(value = "差异率%")
    private BigDecimal diffRate;

    /**
     * 变化类型(新增/减少/变更)
     */
    @ExcelProperty(value = "变化类型")
    private String changeType;
}