package org.dromara.budget.domain.vo;

import org.dromara.budget.domain.BudgetGatherMap;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 预算科目归集映射视图对象 budget_gather_map
 *
 * @author Lion Li
 * @date 2026-09-16
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BudgetGatherMap.class)
public class BudgetGatherMapVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @ExcelProperty(value = "主键ID")
    private Long id;

    /**
     * 方案ID: 0=基础/默认
     */
    @ExcelProperty(value = "方案ID")
    private Long planId;

    /**
     * 源部门ID(本部内设部门)
     */
    @ExcelProperty(value = "源部门ID")
    private Long deptId;

    /**
     * 源部门名称
     */
    @ExcelProperty(value = "源部门名称")
    private String deptName;

    /**
     * 源预算表编号(通常为17)
     */
    @ExcelProperty(value = "源预算表编号")
    private String srcTemplateCode;

    /**
     * 源科目编码(17表明细)
     */
    @ExcelProperty(value = "源科目编码")
    private String srcItemCode;

    /**
     * 源科目名称
     */
    @ExcelProperty(value = "源科目名称")
    private String srcItemName;

    /**
     * 目标预算表编号(通常为06)
     */
    @ExcelProperty(value = "目标预算表编号")
    private String tgtTemplateCode;

    /**
     * 目标部门ID(NULL/空=本部)
     */
    @ExcelProperty(value = "目标部门ID")
    private Long tgtDeptId;

    /**
     * 目标部门名称
     */
    @ExcelProperty(value = "目标部门名称")
    private String tgtDeptName;

    /**
     * 目标科目编码(06表汇总科目)
     */
    @ExcelProperty(value = "目标科目编码")
    private String tgtItemCode;

    /**
     * 目标科目名称
     */
    @ExcelProperty(value = "目标科目名称")
    private String tgtItemName;

    /**
     * 源预算表名称
     */
    @ExcelProperty(value = "源预算表名称")
    private String srcTemplateName;

    /**
     * 目标预算表名称
     */
    @ExcelProperty(value = "目标预算表名称")
    private String tgtTemplateName;
}