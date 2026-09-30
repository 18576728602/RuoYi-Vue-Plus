package org.dromara.budget.domain.vo;

import org.dromara.budget.domain.BudgetData;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;



/**
 * 预算填报数据视图对象 budget_data
 *
 * @author Lion Li
 * @date 2026-08-29
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BudgetData.class)
public class BudgetDataVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @ExcelProperty(value = "主键ID")
    private Long id;

    /**
     * 预算方案ID
     */
    @ExcelProperty(value = "预算方案ID")
    private Long planId;

    /**
     * 组织ID(单位)
     */
    @ExcelProperty(value = "组织ID(单位)")
    private Long orgId;

    /**
     * 部门ID(集团本部部门填报时)
     */
    @ExcelProperty(value = "部门ID(集团本部部门填报时)")
    private Long deptId;

    /**
     * 预算表编号
     */
    @ExcelProperty(value = "预算表编号")
    private String templateCode;

    /**
     * 科目编码
     */
    @ExcelProperty(value = "科目编码")
    private String itemCode;

    /**
     * 预算金额
     */
    @ExcelProperty(value = "预算金额")
    private BigDecimal budgetAmount;

    /**
     * 上年实际金额
     */
    @ExcelProperty(value = "上年实际金额")
    private BigDecimal lastActual;

    /**
     * 实际执行金额
     */
    @ExcelProperty(value = "实际执行金额")
    private BigDecimal executionAmount;

    /**
     * 数据版本: BUDGET=预算数, ACTUAL=上年实际, EXECUTION=执行数
     */
    @ExcelProperty(value = "数据版本: BUDGET=预算数, ACTUAL=上年实际, EXECUTION=执行数")
    private String dataVersion;

    /**
     * 填报说明
     */
    @ExcelProperty(value = "填报说明")
    private String remark;

    /**
     * 状态: DRAFT=草稿, SUBMITTED=已提交, APPROVED=已审批, REJECTED=已驳回
     */
    @ExcelProperty(value = "状态: DRAFT=草稿, SUBMITTED=已提交, APPROVED=已审批, REJECTED=已驳回")
    private String status;


}