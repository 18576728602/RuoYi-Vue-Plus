package org.dromara.budget.domain.vo;

import org.dromara.budget.domain.BudgetTemplateItem;
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
 * 预算模板科目视图对象 budget_template_item
 *
 * @author Lion Li
 * @date 2026-08-29
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BudgetTemplateItem.class)
public class BudgetTemplateItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @ExcelProperty(value = "主键ID")
    private Long id;

    /**
     * 方案ID: 0=基础模板
     */
    private Long planId;

    /**
     * 来源方案ID(空=本方案新增)
     */
    private Long fromPlanId;

    /**
     * 预算表编号(01-16)
     */
    @ExcelProperty(value = "预算表编号(01-16)")
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
     * 上级科目编码
     */
    @ExcelProperty(value = "上级科目编码")
    private String parentCode;

    /**
     * 层级
     */
    @ExcelProperty(value = "层级")
    private Long itemLevel;

    /**
     * 排序号
     */
    @ExcelProperty(value = "排序号")
    private Long itemOrder;

    /**
     * 负责部门编码
     */
    @ExcelProperty(value = "负责部门编码")
    private String responsibleDept;

    /**
     * 是否汇总行: 0=否, 1=是
     */
    @ExcelProperty(value = "是否汇总行: 0=否, 1=是")
    private Long isSummary;

    /**
     * 是否可编辑: 0=否, 1=是
     */
    @ExcelProperty(value = "是否可编辑: 0=否, 1=是")
    private Long isEditable;

    /**
     * 计算公式(如: SUM(children))
     */
    @ExcelProperty(value = "计算公式(如: SUM(children))")
    private String formula;

    /**
     * 适用公司ID(逗号分隔,空=全部公司)
     */
    private String orgScope;

    /**
     * 行类型: HEAD=分类标题/ITEM=明细/SUM=汇总/REF=跨表引用/LINK=链接/NOTE=备注/CALC=计算(空=按明细推断)
     */
    private String rowType;

    /**
     * 单元格自定义内容(JSON: {列key: 文本}, 供 Excel 式自由填写)
     */
    private String cellData;


}
