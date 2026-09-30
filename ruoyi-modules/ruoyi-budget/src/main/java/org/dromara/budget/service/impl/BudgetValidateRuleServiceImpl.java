package org.dromara.budget.service.impl;

import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.BudgetValidateRule;
import org.dromara.budget.domain.bo.BudgetValidateRuleBo;
import org.dromara.budget.domain.vo.BudgetValidateRuleVo;
import org.dromara.budget.mapper.BudgetValidateRuleMapper;
import org.dromara.budget.service.IBudgetValidateRuleService;
import org.dromara.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetValidateRuleServiceImpl implements IBudgetValidateRuleService {

    private final BudgetValidateRuleMapper baseMapper;

    @Override
    public List<BudgetValidateRuleVo> list() {
        LambdaQueryWrapper<BudgetValidateRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(BudgetValidateRule::getSortOrder);
        List<BudgetValidateRule> rules = baseMapper.selectList(wrapper);
        return rules.stream().map(r -> BeanUtil.copyProperties(r, BudgetValidateRuleVo.class)).collect(Collectors.toList());
    }

    @Override
    public BudgetValidateRuleVo getById(Long id) {
        BudgetValidateRule rule = baseMapper.selectById(id);
        if (rule == null) throw new ServiceException("校验规则不存在");
        return BeanUtil.copyProperties(rule, BudgetValidateRuleVo.class);
    }

    @Override
    public void add(BudgetValidateRuleBo bo) {
        BudgetValidateRule rule = BeanUtil.copyProperties(bo, BudgetValidateRule.class);
        if (rule.getEnabled() == null) rule.setEnabled(1L);
        if (rule.getSortOrder() == null) rule.setSortOrder(0);
        baseMapper.insert(rule);
    }

    @Override
    public void update(BudgetValidateRuleBo bo) {
        BudgetValidateRule existing = baseMapper.selectById(bo.getId());
        if (existing == null) throw new ServiceException("校验规则不存在");
        BeanUtil.copyProperties(bo, existing);
        baseMapper.updateById(existing);
    }

    @Override
    public void delete(Long id) {
        BudgetValidateRule rule = baseMapper.selectById(id);
        if (rule == null) throw new ServiceException("校验规则不存在");
        baseMapper.deleteById(id);
    }

    @Override
    public void toggleEnabled(Long id, Long enabled) {
        BudgetValidateRule rule = baseMapper.selectById(id);
        if (rule == null) throw new ServiceException("校验规则不存在");
        rule.setEnabled(enabled);
        baseMapper.updateById(rule);
    }
}
