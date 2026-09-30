package org.dromara.budget.domain.bo;

import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;

/**
 * 预算模板科目业务对象 budget_template_item
 *
 * @author Lion Li
 * @date 2026-08-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BudgetTemplateItem.class, reverseConvertGenerate = false)
public class BudgetTemplateItemBo extends BaseEntity {

    /**
     * 主键ID
     */
    @NotNull(message = "主键ID不能为空", groups = { EditGroup.class })
    private Long id;

    /**
     * 方案ID: 0=基础模板
     */
    private Long planId;

    /**
     * 预算表编号(01-16)
     */
    @NotBlank(message = "预算表编号(01-16)不能为空", groups = { AddGroup.class, EditGroup.class })
    private String templateCode;

    /**
     * 预算表名称
     */
    @NotBlank(message = "预算表名称不能为空", groups = { AddGroup.class, EditGroup.class })
    private String templateName;

    /**
     * 科目编码
     */
    @NotBlank(message = "科目编码不能为空", groups = { AddGroup.class, EditGroup.class })
    private String itemCode;

    /**
     * 科目名称
     */
    @NotBlank(message = "科目名称不能为空", groups = { AddGroup.class, EditGroup.class })
    private String itemName;

    /**
     * 上级科目编码
     */
    private String parentCode;

    /**
     * 层级
     */
    private Long itemLevel;

    /**
     * 排序号
     */
    private Long itemOrder;

    /**
     * 负责部门编码
     */
    private String responsibleDept;

    /**
     * 是否汇总行: 0=否, 1=是
     */
    private Long isSummary;

    /**
     * 是否可编辑: 0=否, 1=是
     */
    private Long isEditable;

    /**
     * 计算公式(如: SUM(children))
     */
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
