package org.dromara.budget.service;

import org.dromara.budget.domain.bo.BudgetValidateRuleBo;
import org.dromara.budget.domain.vo.BudgetValidateRuleVo;
import java.util.List;

public interface IBudgetValidateRuleService {

    List<BudgetValidateRuleVo> list();

    BudgetValidateRuleVo getById(Long id);

    void add(BudgetValidateRuleBo bo);

    void update(BudgetValidateRuleBo bo);

    void delete(Long id);

    void toggleEnabled(Long id, Long enabled);
}
