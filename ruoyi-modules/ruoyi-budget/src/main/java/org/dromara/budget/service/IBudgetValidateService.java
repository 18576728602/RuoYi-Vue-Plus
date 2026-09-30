package org.dromara.budget.service;

import org.dromara.budget.domain.bo.BudgetValidateQuery;
import org.dromara.budget.domain.vo.BudgetValidateResult;

/**
 * 填报校验
 *
 * <p>三类校验（PRD 7.3）：逻辑校验(汇总行=明细之和)、合理性校验(同比/占比预警)、
 * 完整性校验(必填科目已填报)。实时校验(保存时)与提交校验通用。
 *
 * @author Lion Li
 * @date 2026-09-14
 */
public interface IBudgetValidateService {

    /**
     * 执行填报校验，返回校验结果清单与可提交状态
     */
    BudgetValidateResult validate(BudgetValidateQuery q);
}