package org.dromara.budget.domain;

import io.github.linpeilie.annotations.AutoMapper;
import org.dromara.budget.domain.vo.BudgetCategoryVo;
import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 预算业务分类对象 budget_category
 *
 * <p>独立分类字典，与科目明细解耦。科目主数据通过 category_code 归属某个分类，
 * 分类下平铺明细（无第二层板块层级）。分类编码作为业务关联唯一键，独立于雪花ID。
 *
 * @author Lion Li
 * @date 2026-09-28
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_category")
@AutoMapper(target = BudgetCategoryVo.class, reverseConvertGenerate = false)
public class BudgetCategory extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID(雪花,仅作物理主键)
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 分类编码(业务关联唯一键,如 01/02/03...)
     */
    private String categoryCode;

    /**
     * 分类名称(如 公司基本信息/营业收入/营业成本/费用/投资 等)
     */
    private String categoryName;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 备注
     */
    private String remark;

    /**
     * 乐观锁版本号
     */
    @Version
    private Long version;

    /**
     * 删除标志:0=正常,1=删除
     */
    @TableLogic
    private Long delFlag;

}