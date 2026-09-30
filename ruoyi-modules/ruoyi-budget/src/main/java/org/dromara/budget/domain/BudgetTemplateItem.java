package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 预算模板科目对象 budget_template_item
 *
 * @author Lion Li
 * @date 2026-08-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_template_item")
public class BudgetTemplateItem extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 方案ID: 0=基础模板
     */
    private Long planId;

    /**
     * 来源方案ID(复制自哪个方案, 空=本方案新增)
     */
    private Long fromPlanId;

    /**
     * 来源预算表ID(预算表自身挂载清单时使用, 方案挂接为空)
     */
    private Long templateId;

    /**
     * 预算年度(同一编码不同年度的预算表版本)
     */
    private Integer budgetYear;

    /**
     * 预算表编号(01-16)
     */
    private String templateCode;

    /**
     * 预算表名称
     */
    private String templateName;

    /**
     * 科目编码
     */
    private String itemCode;

    /**
     * 科目名称
     */
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

    /**
     * 指向科目主数据ID(空=历史副本)
     */
    private Long refSubjectId;

    /**
     * 乐观锁版本号
     */
    @Version
    private Long version;

    /**
     * 删除标志: 0=正常, 其他=已删除
     */
    @TableLogic
    private Long delFlag;


}
