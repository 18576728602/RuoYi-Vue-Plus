package org.dromara.budget.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.BudgetFieldVisibility;
import org.dromara.budget.domain.BudgetTemplate;
import org.dromara.budget.domain.bo.BudgetFieldVisibilityBo;
import org.dromara.budget.domain.vo.BudgetFieldVisibilityVo;
import org.dromara.budget.mapper.BudgetFieldVisibilityMapper;
import org.dromara.budget.mapper.BudgetTemplateMapper;
import org.dromara.budget.service.IBudgetFieldVisibilityService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预算表字段可见性规则Service业务层处理
 *
 * @author Lion Li
 * @date 2026-09-28
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetFieldVisibilityServiceImpl implements IBudgetFieldVisibilityService {

    private final BudgetFieldVisibilityMapper baseMapper;

    private final BudgetTemplateMapper budgetTemplateMapper;

    @Override
    public TableDataInfo<BudgetFieldVisibilityVo> queryPage(Long templateId, Long planId, Integer budgetYear, PageQuery pageQuery) {
        LambdaQueryWrapper<BudgetFieldVisibility> qw = buildWrapper(templateId, planId, budgetYear);
        IPage<BudgetFieldVisibilityVo> page = baseMapper.selectVoPage(pageQuery.build(), qw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<BudgetFieldVisibilityVo> queryRules(Long templateId, Long planId, Integer budgetYear) {
        return baseMapper.selectVoList(buildWrapper(templateId, planId, budgetYear)
            .orderByAsc(BudgetFieldVisibility::getOrgId));
    }

    private LambdaQueryWrapper<BudgetFieldVisibility> buildWrapper(Long templateId, Long planId, Integer budgetYear) {
        LambdaQueryWrapper<BudgetFieldVisibility> qw = new LambdaQueryWrapper<>();
        if (templateId != null) {
            qw.and(w -> w.isNull(BudgetFieldVisibility::getTemplateId)
                .or().eq(BudgetFieldVisibility::getTemplateId, templateId));
        }
        if (planId == null) {
            qw.eq(BudgetFieldVisibility::getPlanId, 0L);
        } else {
            qw.and(w -> w.eq(BudgetFieldVisibility::getPlanId, 0L)
                .or().eq(BudgetFieldVisibility::getPlanId, planId));
        }
        qw.and(w -> w.isNull(BudgetFieldVisibility::getBudgetYear)
            .or(budgetYear != null, x -> x.eq(BudgetFieldVisibility::getBudgetYear, budgetYear)));
        return qw;
    }

    @Override
    public Map<Long, String> resolvePermissions(Long templateId, Long planId, Integer budgetYear,
                                                Long orgId, Collection<Long> subjectIds) {
        Map<Long, String> result = new HashMap<>();
        if (orgId == null || CollUtil.isEmpty(subjectIds)) {
            return result;
        }
        // 若未传年度但传了预算表ID，自动从预算表推导年度（兼容前端未传年度的场景）
        if (budgetYear == null && templateId != null) {
            BudgetTemplate tpl = budgetTemplateMapper.selectById(templateId);
            if (tpl != null && tpl.getBudgetYear() != null) {
                budgetYear = tpl.getBudgetYear();
            }
        }
        final Integer finalYear = budgetYear;
        LambdaQueryWrapper<BudgetFieldVisibility> qw = new LambdaQueryWrapper<>();
        qw.eq(BudgetFieldVisibility::getOrgId, orgId)
            .in(BudgetFieldVisibility::getRefSubjectId, subjectIds)
            .and(w -> w.isNull(BudgetFieldVisibility::getTemplateId)
                .or().eq(BudgetFieldVisibility::getTemplateId, templateId));
        if (planId == null) {
            qw.eq(BudgetFieldVisibility::getPlanId, 0L);
        } else {
            qw.and(w -> w.eq(BudgetFieldVisibility::getPlanId, 0L)
                .or().eq(BudgetFieldVisibility::getPlanId, planId));
        }
        // 年度：规则为空=跨年度；提供年度时=跨年度 或 精确年度
        qw.and(w -> w.isNull(BudgetFieldVisibility::getBudgetYear)
            .or(finalYear != null, x -> x.eq(BudgetFieldVisibility::getBudgetYear, finalYear)));

        List<BudgetFieldVisibility> rules = baseMapper.selectList(qw);
        // 同一科目下可能有多条(表级/方案级/年度级各不相同)，取"最具体"者
        Map<Long, BudgetFieldVisibility> best = new HashMap<>();
        for (BudgetFieldVisibility r : rules) {
            BudgetFieldVisibility cur = best.get(r.getRefSubjectId());
            if (cur == null || specificity(r) > specificity(cur)) {
                best.put(r.getRefSubjectId(), r);
            }
        }
        for (Map.Entry<Long, BudgetFieldVisibility> e : best.entrySet()) {
            result.put(e.getKey(), e.getValue().getFieldPermission());
        }
        return result;
    }

    /** 规则具体度评分：维度从空->精确逐级加分，分高者更优先 */
    private int specificity(BudgetFieldVisibility r) {
        int score = 0;
        if (r.getTemplateId() != null) score += 4;
        if (r.getPlanId() != null && r.getPlanId() != 0L) score += 2;
        if (r.getBudgetYear() != null) score += 1;
        return score;
    }

    @Override
    public BudgetFieldVisibilityVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean saveRule(BudgetFieldVisibilityBo bo) {
        if (bo.getRefSubjectId() == null || bo.getOrgId() == null) {
            throw new ServiceException("请指定科目(字段)与目标子公司");
        }
        String perm = bo.getFieldPermission();
        if (!"HIDE".equals(perm) && !"READONLY".equals(perm) && !"EDIT".equals(perm)) {
            throw new ServiceException("字段权限仅支持 HIDE/READONLY/EDIT");
        }
        BudgetFieldVisibility entity = bo.getId() == null
            ? new BudgetFieldVisibility()
            : baseMapper.selectById(bo.getId());
        if (entity == null) {
            entity = new BudgetFieldVisibility();
        }
        // 幂等：按 科目×公司×表×方案×年度 唯一键 upsert
        BudgetFieldVisibility byKey = baseMapper.selectOne(new LambdaQueryWrapper<BudgetFieldVisibility>()
            .eq(BudgetFieldVisibility::getRefSubjectId, bo.getRefSubjectId())
            .eq(BudgetFieldVisibility::getOrgId, bo.getOrgId())
            .eq(BudgetFieldVisibility::getTemplateId, bo.getTemplateId())
            .eq(BudgetFieldVisibility::getPlanId, bo.getPlanId() == null ? 0L : bo.getPlanId())
            .eq(BudgetFieldVisibility::getBudgetYear, bo.getBudgetYear())
            .last("LIMIT 1"));
        if (byKey != null && byKey.getId() != null) {
            entity.setId(byKey.getId());
        }
        entity.setTemplateId(bo.getTemplateId());
        entity.setPlanId(bo.getPlanId() == null ? 0L : bo.getPlanId());
        entity.setBudgetYear(bo.getBudgetYear());
        entity.setRefSubjectId(bo.getRefSubjectId());
        entity.setOrgId(bo.getOrgId());
        entity.setFieldPermission(bo.getFieldPermission());
        entity.setRemark(bo.getRemark());
        return entity.getId() != null ? baseMapper.updateById(entity) > 0 : baseMapper.insert(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean removeByIds(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Boolean.FALSE;
        }
        return baseMapper.deleteByIds(ids) > 0;
    }
}
