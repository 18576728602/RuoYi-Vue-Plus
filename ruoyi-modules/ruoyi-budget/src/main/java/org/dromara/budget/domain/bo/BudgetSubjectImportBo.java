package org.dromara.budget.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 从科目主数据库导入到方案 请求体
 *
 * @author Lion Li
 */
@Data
public class BudgetSubjectImportBo {

    /**
     * 目标方案ID
     */
    @NotNull(message = "方案ID不能为空")
    private Long planId;

    /**
     * 选中的科目主数据ID集合
     */
    @NotEmpty(message = "请至少选择一个科目")
    private List<Long> subjectIds;
}