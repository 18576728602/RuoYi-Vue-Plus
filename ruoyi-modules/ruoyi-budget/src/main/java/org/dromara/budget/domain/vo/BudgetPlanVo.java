package org.dromara.budget.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.dromara.budget.domain.BudgetPlan;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;



/**
 * 预算方案视图对象 budget_plan
 *
 * @author Lion Li
 * @date 2026-08-29
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BudgetPlan.class)
public class BudgetPlanVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @ExcelProperty(value = "主键ID")
    private Long id;

    /**
     * 方案编码
     */
    @ExcelProperty(value = "方案编码")
    private String planCode;

    /**
     * 方案名称
     */
    @ExcelProperty(value = "方案名称")
    private String planName;

    /**
     * 预算年度
     */
    @ExcelProperty(value = "预算年度")
    private Long budgetYear;

    /**
     * 填报开始日期
     */
    @ExcelProperty(value = "填报开始日期")
    private Date startDate;

    /**
     * 填报截止日期
     */
    @ExcelProperty(value = "填报截止日期")
    private Date endDate;

    /**
     * 状态: DRAFT=草稿, PUBLISHED=已发布, CLOSED=已关闭, ARCHIVED=已归档
     */
    @ExcelProperty(value = "状态: DRAFT=草稿, PUBLISHED=已发布, CLOSED=已关闭, ARCHIVED=已归档")
    private String status;

    /**
     * 审批流程JSON配置
     */
    @ExcelProperty(value = "审批流程JSON配置")
    private String approvalFlow;

    /**
     * 填报公司范围: 逗号分隔公司ID, 空=全部公司
     */
    private String orgScope;

    /**
     * 方案说明
     */
    @ExcelProperty(value = "方案说明")
    private String description;


}
