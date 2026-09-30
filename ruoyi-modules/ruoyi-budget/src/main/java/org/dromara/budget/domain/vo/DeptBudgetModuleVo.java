package org.dromara.budget.domain.vo;

import org.dromara.budget.domain.DeptBudgetModule;
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
 * 部门-预算板块映射视图对象 dept_budget_module
 *
 * @author Lion Li
 * @date 2026-08-29
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = DeptBudgetModule.class)
public class DeptBudgetModuleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @ExcelProperty(value = "主键ID")
    private Long id;

    /**
     * 部门ID
     */
    @ExcelProperty(value = "部门ID")
    private Long deptId;

    /**
     * 预算表编号
     */
    @ExcelProperty(value = "预算表编号")
    private String templateCode;

    /**
     * 负责科目编码列表(逗号分隔)
     */
    @ExcelProperty(value = "负责科目编码列表(逗号分隔)")
    private String itemCodes;


}
