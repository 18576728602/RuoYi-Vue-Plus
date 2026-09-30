package org.dromara.budget.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.dromara.budget.domain.BudgetCategory;

/**
 * 预算业务分类业务对象
 *
 * @author Lion Li
 * @date 2026-09-28
 */
@Data
@AutoMapper(target = BudgetCategory.class, reverseConvertGenerate = false)
public class BudgetCategoryBo {

    /**
     * 主键ID(空=新增)
     */
    private Long id;

    /**
     * 分类编码(业务关联唯一键,新增时可由后端自动生成)
     */
    @NotBlank(message = "分类编码不能为空")
    private String categoryCode;

    /**
     * 分类名称
     */
    @NotBlank(message = "分类名称不能为空")
    private String categoryName;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 备注
     */
    private String remark;
}