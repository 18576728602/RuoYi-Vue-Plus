package org.dromara.budget.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.BudgetControlRule;
import org.dromara.budget.domain.bo.BudgetControlRuleBo;
import org.dromara.budget.domain.vo.BudgetControlResult;
import org.dromara.budget.domain.vo.BudgetControlRuleVo;
import org.dromara.budget.mapper.BudgetControlRuleMapper;
import org.dromara.budget.service.IBudgetControlRuleService;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 预算控制规则(事前拦截) Service实现
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetControlRuleServiceImpl implements IBudgetControlRuleService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal DEFAULT_WARN = new BigDecimal("80");
    private static final BigDecimal DEFAULT_BLOCK = new BigDecimal("100");

    private final BudgetControlRuleMapper baseMapper;

    @Override
    public Boolean insertByBo(BudgetControlRuleBo bo) {
        validate(bo);
        BudgetControlRule entity = MapstructUtils.convert(bo, BudgetControlRule.class);
        return baseMapper.insert(entity) > 0;
    }

    @Override
    public Boolean updateByBo(BudgetControlRuleBo bo) {
        if (bo.getId() == null) {
            throw new org.dromara.common.core.exception.ServiceException("规则ID不能为空");
        }
        validate(bo);
        BudgetControlRule entity = MapstructUtils.convert(bo, BudgetControlRule.class);
        return baseMapper.updateById(entity) > 0;
    }

    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (ObjectUtil.isNotEmpty(ids)) {
            return baseMapper.deleteByIds(ids) > 0;
        }
        return Boolean.FALSE;
    }

    @Override
    public List<BudgetControlRuleVo> queryList(BudgetControlRuleBo bo) {
        return baseMapper.selectVoList(buildWrapper(bo));
    }

    @Override
    public TableDataInfo<BudgetControlRuleVo> queryPageList(BudgetControlRuleBo bo, PageQuery pageQuery) {
        IPage<BudgetControlRuleVo> page = baseMapper.selectVoPage(pageQuery.build(), buildWrapper(bo));
        return TableDataInfo.build(page);
    }

    @Override
    public Boolean toggleEnabled(Long id, Boolean enabled) {
        if (id == null) {
            throw new org.dromara.common.core.exception.ServiceException("规则ID不能为空");
        }
        LambdaQueryWrapper<BudgetControlRule> uw = new LambdaQueryWrapper<>();
        uw.eq(BudgetControlRule::getId, id);
        BudgetControlRule update = new BudgetControlRule();
        update.setEnabled(enabled != null && enabled ? Boolean.TRUE : Boolean.FALSE);
        return baseMapper.update(update, uw) > 0;
    }

    @Override
    public BudgetControlRule matchRule(Long planId, Long orgId, String templateCode) {
        // 只取启用规则，按 sort_order 升序、id 升序保证稳定
        LambdaQueryWrapper<BudgetControlRule> qw = new LambdaQueryWrapper<>();
        qw.eq(BudgetControlRule::getEnabled, Boolean.TRUE);
        qw.orderByAsc(BudgetControlRule::getSortOrder);
        qw.orderByAsc(BudgetControlRule::getId);
        List<BudgetControlRule> rules = baseMapper.selectList(qw);
        if (rules.isEmpty()) {
            return null;
        }
        // 按匹配精确度打分，分高者优先：方案+单位+表 > 方案+单位 > 方案+表 > 方案 > 单位 > 表 > 全局
        BudgetControlRule best = null;
        int bestScore = -1;
        for (BudgetControlRule r : rules) {
            int score = 0;
            if (r.getPlanId() != null) {
                if (planId != null && planId.equals(r.getPlanId())) score += 8; else continue;
            }
            if (r.getOrgId() != null) {
                if (orgId != null && orgId.equals(r.getOrgId())) score += 4; else continue;
            }
            if (StrUtil.isNotBlank(r.getTemplateCode())) {
                if (StrUtil.isNotBlank(templateCode) && templateCode.equals(r.getTemplateCode())) score += 2; else continue;
            }
            // 已按 sort_order/id 排序，score 相等时取最先的(优先级/sort 更小)
            if (score > bestScore) {
                bestScore = score;
                best = r;
            }
        }
        return best;
    }

    @Override
    public BudgetControlResult evaluate(Long planId, Long orgId, String templateCode,
                                        BigDecimal budget, BigDecimal executionSum, List<BigDecimal> quarters) {
        // 预算为空/<=0：不做控制（行业通用"预算数为空时不控制"）
        if (budget == null || budget.signum() <= 0) {
            return BudgetControlResult.of("GREEN", false, BigDecimal.ZERO, budget, executionSum, null, null);
        }
        BudgetControlRule rule = matchRule(planId, orgId, templateCode);
        if (rule == null) {
            return BudgetControlResult.of("GREEN", false, BigDecimal.ZERO, budget, executionSum, null,
                "未配置控制规则，不拦截");
        }

        BigDecimal exec = executionSum;
        BigDecimal budgetRef = budget;
        // 当期口径：取最大单季度执行，预算按 年预算/4
        if ("CURRENT".equalsIgnoreCase(rule.getControlScope())) {
            BigDecimal maxQ = BigDecimal.ZERO;
            if (quarters != null) {
                for (BigDecimal q : quarters) {
                    if (q != null && q.compareTo(maxQ) > 0) {
                        maxQ = q;
                    }
                }
            }
            exec = maxQ;
            budgetRef = budget.divide(BigDecimal.valueOf(4), 6, RoundingMode.HALF_UP);
        }

        BigDecimal rate = exec.multiply(HUNDRED).divide(budgetRef, 2, RoundingMode.HALF_UP);

        BigDecimal warn = rule.getWarnPercent() != null ? rule.getWarnPercent() : DEFAULT_WARN;
        BigDecimal block = rule.getBlockPercent() != null ? rule.getBlockPercent() : DEFAULT_BLOCK;

        String level;
        if (rate.compareTo(block) >= 0) {
            level = "RED";
        } else if (rate.compareTo(warn) >= 0) {
            level = "YELLOW";
        } else {
            level = "GREEN";
        }

        String strength = rule.getControlStrength();
        // 仅刚性控制且达控制阈值时阻断
        boolean blocked = "RIGID".equalsIgnoreCase(strength) && "RED".equals(level);

        String message;
        if ("GREEN".equals(level)) {
            message = "执行率 " + rate + "%（阈值 " + warn + "%/" + block + "%），在控制范围内";
        } else if ("YELLOW".equals(level)) {
            message = "执行率 " + rate + "% 已达提示阈值 " + warn + "%（黄），请关注预算使用；仍未达控制阈值 " + block + "%";
        } else {
            String blockTip = "RIGID".equalsIgnoreCase(strength)
                ? "已按刚性控制阻止提交，请先发起预算调整/特批"
                : ("FLEXIBLE".equalsIgnoreCase(strength)
                    ? "已按柔性控制放行，请及时发起预算调整/特批评审"
                    : "已按预警控制放行，仅提醒");
            message = "执行率 " + rate + "% 已超过控制阈值 " + block + "%（红）。" + blockTip;
        }
        return BudgetControlResult.of(level, blocked, rate, budgetRef, exec, strength, message);
    }

    private LambdaQueryWrapper<BudgetControlRule> buildWrapper(BudgetControlRuleBo bo) {
        LambdaQueryWrapper<BudgetControlRule> qw = new LambdaQueryWrapper<>();
        if (bo != null) {
            qw.eq(ObjectUtil.isNotNull(bo.getPlanId()), BudgetControlRule::getPlanId, bo.getPlanId());
            qw.eq(ObjectUtil.isNotNull(bo.getOrgId()), BudgetControlRule::getOrgId, bo.getOrgId());
            qw.eq(StrUtil.isNotBlank(bo.getTemplateCode()), BudgetControlRule::getTemplateCode, bo.getTemplateCode());
            qw.eq(StrUtil.isNotBlank(bo.getControlStrength()), BudgetControlRule::getControlStrength, bo.getControlStrength());
        }
        qw.orderByDesc(BudgetControlRule::getEnabled);
        qw.orderByAsc(BudgetControlRule::getSortOrder);
        qw.orderByAsc(BudgetControlRule::getId);
        return qw;
    }

    private void validate(BudgetControlRuleBo bo) {
        String strength = bo.getControlStrength();
        if (strength == null || !Set.of("RIGID", "FLEXIBLE", "WARN").contains(strength)) {
            throw new org.dromara.common.core.exception.ServiceException("控制强度不合法，仅支持 RIGID/FLEXIBLE/WARN");
        }
        String scope = StrUtil.isBlank(bo.getControlScope()) ? "ACCUM" : bo.getControlScope();
        if (!Set.of("ACCUM", "CURRENT").contains(scope)) {
            throw new org.dromara.common.core.exception.ServiceException("控制口径不合法，仅支持 ACCUM/CURRENT");
        }
        bo.setControlScope(scope);
        BigDecimal block = bo.getBlockPercent();
        BigDecimal warn = bo.getWarnPercent();
        if (block != null && warn != null && warn.compareTo(block) >= 0) {
            throw new org.dromara.common.core.exception.ServiceException("提示阈值%必须小于控制阈值%");
        }
    }
}