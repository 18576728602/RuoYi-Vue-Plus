package org.dromara.budget.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.BudgetData;
import org.dromara.budget.domain.BudgetTarget;
import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.budget.domain.bo.BudgetTargetBo;
import org.dromara.budget.mapper.BudgetDataMapper;
import org.dromara.budget.mapper.BudgetTargetMapper;
import org.dromara.budget.mapper.BudgetTemplateItemMapper;
import org.dromara.budget.service.IBudgetTargetService;
import org.dromara.budget.util.BudgetScopeUtil;
import org.dromara.system.domain.SysDept;
import org.dromara.system.mapper.SysDeptMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 预算目标下达 Service实现（一下）
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetTargetServiceImpl implements IBudgetTargetService {

    private final BudgetTargetMapper targetMapper;
    private final BudgetTemplateItemMapper templateItemMapper;
    private final BudgetDataMapper budgetDataMapper;
    private final SysDeptMapper sysDeptMapper;

    private static final String PUBLISHED = "PUBLISHED";
    private static final String DRAFT = "DRAFT";

    @Override
    public List<Map<String, Object>> listCompanyTargets(Long planId) {
        List<SysDept> allDepts = sysDeptMapper.selectList(null);
        List<SysDept> companies = allDepts.stream()
            .filter(d -> d.getParentId() != null && d.getParentId() != 0)
            .filter(d -> {
                Optional<SysDept> parentOpt = allDepts.stream().filter(p -> p.getDeptId().equals(d.getParentId())).findFirst();
                if (parentOpt.isEmpty()) return true;
                SysDept grand = parentOpt.get().getParentId() == null ? null
                    : allDepts.stream().filter(g -> g.getDeptId().equals(parentOpt.get().getParentId())).findFirst().orElse(null);
                return grand == null || grand.getParentId() == null || grand.getParentId() != 0;
            })
            .sorted(Comparator.comparing(SysDept::getOrderNum))
            .collect(Collectors.toList());
        if (companies.isEmpty()) {
            companies = allDepts.stream()
                .filter(d -> d.getParentId() != null && d.getParentId() != 0)
                .filter(d -> d.getDeptCategory() == null || !d.getDeptCategory().contains("D"))
                .sorted(Comparator.comparing(SysDept::getOrderNum))
                .collect(Collectors.toList());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (SysDept d : companies) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("deptId", d.getDeptId());
            m.put("deptName", d.getDeptName());
            // 目标状态摘要
            Map<String, Object> summary = companySummary(planId, d.getDeptId());
            m.put("status", summary.get("status"));       // PUBLISHED / DRAFT / NONE
            m.put("versionNo", summary.get("versionNo"));
            m.put("itemCount", summary.get("itemCount"));
            m.put("totalAmount", summary.get("totalAmount"));
            m.put("updateTime", summary.get("updateTime"));
            result.add(m);
        }
        return result;
    }

    private Map<String, Object> companySummary(Long planId, Long deptId) {
        Map<String, Object> m = new HashMap<>();
        LambdaQueryWrapper<BudgetTarget> qw = new LambdaQueryWrapper<>();
        qw.eq(BudgetTarget::getPlanId, planId).eq(BudgetTarget::getDeptId, deptId);
        List<BudgetTarget> rows = targetMapper.selectList(qw);
        if (rows.isEmpty()) {
            m.put("status", "NONE");
            m.put("versionNo", null);
            m.put("itemCount", 0);
            m.put("totalAmount", new BigDecimal("0.00"));
            m.put("updateTime", null);
            return m;
        }
        Integer maxV = rows.stream().map(BudgetTarget::getVersionNo).max(Integer::compareTo).orElse(1);
        List<BudgetTarget> active = rows.stream().filter(r -> Objects.equals(r.getVersionNo(), maxV)).collect(Collectors.toList());
        String status = active.stream().anyMatch(r -> PUBLISHED.equals(r.getStatus())) ? PUBLISHED : DRAFT;
        BigDecimal total = active.stream().map(BudgetTarget::getTargetAmount)
            .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        Date updateTime = active.stream().map(BudgetTarget::getUpdateTime).filter(Objects::nonNull)
            .max(Date::compareTo).orElse(null);
        m.put("status", status);
        m.put("versionNo", maxV);
        m.put("itemCount", active.size());
        m.put("totalAmount", total);
        m.put("updateTime", updateTime);
        return m;
    }

    @Override
    public List<Map<String, Object>> getDetail(Long planId, Long deptId, String templateCode, Integer versionNo) {
        Integer v = versionNo;
        if (v == null) {
            LambdaQueryWrapper<BudgetTarget> q = new LambdaQueryWrapper<>();
            q.eq(BudgetTarget::getPlanId, planId).eq(BudgetTarget::getDeptId, deptId);
            if (StrUtil.isNotBlank(templateCode)) q.eq(BudgetTarget::getTemplateCode, templateCode);
            List<BudgetTarget> all = targetMapper.selectList(q);
            v = all.stream().map(BudgetTarget::getVersionNo).max(Integer::compareTo).orElse(null);
            if (v == null) return new ArrayList<>();
        }
        LambdaQueryWrapper<BudgetTarget> qw = new LambdaQueryWrapper<>();
        qw.eq(BudgetTarget::getPlanId, planId)
            .eq(BudgetTarget::getDeptId, deptId)
            .eq(BudgetTarget::getVersionNo, v);
        if (StrUtil.isNotBlank(templateCode)) qw.eq(BudgetTarget::getTemplateCode, templateCode);
        qw.orderByAsc(BudgetTarget::getTemplateCode).orderByAsc(BudgetTarget::getItemOrder);
        return targetMapper.selectList(qw).stream().map(this::toRow).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> buildItems(Long planId, Long deptId, String templateCode) {
        LambdaQueryWrapper<BudgetTemplateItem> qw = new LambdaQueryWrapper<>();
        qw.eq(BudgetTemplateItem::getPlanId, planId);
        if (StrUtil.isNotBlank(templateCode)) qw.eq(BudgetTemplateItem::getTemplateCode, templateCode);
        qw.eq(BudgetTemplateItem::getDelFlag, 0)
            .orderByAsc(BudgetTemplateItem::getTemplateCode)
            .orderByAsc(BudgetTemplateItem::getItemOrder);
        List<BudgetTemplateItem> items = templateItemMapper.selectList(qw);

        // 已存在目标覆盖（用于再次编辑）
        Map<String, BudgetTarget> existing = new HashMap<>();
        List<Map<String, Object>> curDetail = getDetail(planId, deptId, templateCode, null);
        for (Map<String, Object> row : curDetail) {
            BudgetTarget t = new BudgetTarget();
            t.setTargetAmount((BigDecimal) row.get("targetAmount"));
            t.setToleranceMin((BigDecimal) row.get("toleranceMin"));
            t.setToleranceMax((BigDecimal) row.get("toleranceMax"));
            existing.put(row.get("templateCode") + "_" + row.get("itemCode"), t);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (BudgetTemplateItem it : items) {
            if (!BudgetScopeUtil.appliesTo(it.getOrgScope(), deptId)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("templateCode", it.getTemplateCode());
            m.put("itemCode", it.getItemCode());
            m.put("itemName", it.getItemName());
            m.put("itemOrder", it.getItemOrder());
            BudgetTarget pre = existing.get(it.getTemplateCode() + "_" + it.getItemCode());
            m.put("targetAmount", pre != null ? pre.getTargetAmount() : null);
            m.put("toleranceMin", pre != null ? pre.getToleranceMin() : null);
            m.put("toleranceMax", pre != null ? pre.getToleranceMax() : null);
            result.add(m);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDraft(BudgetTargetBo bo) {
        int version = activeDraftVersion(bo.getPlanId(), bo.getDeptId());
        // 删除该版本的所有草稿目标行，整体覆盖
        LambdaQueryWrapper<BudgetTarget> del = new LambdaQueryWrapper<>();
        del.eq(BudgetTarget::getPlanId, bo.getPlanId())
            .eq(BudgetTarget::getDeptId, bo.getDeptId())
            .eq(BudgetTarget::getStatus, DRAFT)
            .eq(BudgetTarget::getVersionNo, version);
        targetMapper.delete(del);

        for (BudgetTargetBo.Item it : bo.getItems()) {
            BudgetTarget t = new BudgetTarget();
            t.setPlanId(bo.getPlanId());
            t.setDeptId(bo.getDeptId());
            t.setTemplateCode(it.getTemplateCode());
            t.setItemCode(it.getItemCode());
            t.setItemName(it.getItemName());
            t.setTargetAmount(it.getTargetAmount());
            t.setToleranceMin(it.getToleranceMin());
            t.setToleranceMax(it.getToleranceMax());
            t.setItemOrder(it.getItemOrder());
            t.setVersionNo(version);
            t.setStatus(DRAFT);
            targetMapper.insert(t);
        }
    }

    private int activeDraftVersion(Long planId, Long deptId) {
        Integer draftV = maxVersionByStatus(planId, deptId, DRAFT);
        if (draftV != null) return draftV;
        Integer maxV = maxVersion(planId, deptId);
        return (maxV == null ? 0 : maxV) + 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishTarget(Long planId, Long deptId) {
        LambdaUpdateWrapper<BudgetTarget> uw = new LambdaUpdateWrapper<>();
        uw.eq(BudgetTarget::getPlanId, planId)
            .eq(BudgetTarget::getDeptId, deptId)
            .eq(BudgetTarget::getStatus, DRAFT)
            .set(BudgetTarget::getStatus, PUBLISHED);
        int rows = targetMapper.update(null, uw);
        if (rows == 0) {
            throw new org.dromara.common.core.exception.ServiceException("该公司没有草稿目标可下达，请先编制保存目标");
        }
    }

    @Override
    public List<Map<String, Object>> listHistory(Long planId, Long deptId) {
        LambdaQueryWrapper<BudgetTarget> qw = new LambdaQueryWrapper<>();
        qw.eq(BudgetTarget::getPlanId, planId).eq(BudgetTarget::getDeptId, deptId);
        qw.orderByDesc(BudgetTarget::getVersionNo);
        List<BudgetTarget> rows = targetMapper.selectList(qw);
        Map<Integer, Map<String, Object>> map = new LinkedHashMap<>();
        for (BudgetTarget r : rows) {
            map.computeIfAbsent(r.getVersionNo(), v -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("versionNo", r.getVersionNo());
                m.put("status", r.getStatus());
                m.put("itemCount", 0);
                m.put("totalAmount", new BigDecimal("0.00"));
                m.put("updateTime", r.getUpdateTime());
                return m;
            });
            Map<String, Object> m = map.get(r.getVersionNo());
            m.put("itemCount", (Integer) m.get("itemCount") + 1);
            if (r.getTargetAmount() != null) {
                m.put("totalAmount", ((BigDecimal) m.get("totalAmount")).add(r.getTargetAmount()));
            }
            if (m.get("status") == null || PUBLISHED.equals(m.get("status"))) {
                m.put("updateTime", r.getUpdateTime());
            }
        }
        return new ArrayList<>(map.values());
    }

    @Override
    public List<Map<String, Object>> getMyTargets(Long planId, Long deptId, String templateCode) {
        Integer v = maxVersionByStatus(planId, deptId, PUBLISHED);
        if (v == null) return new ArrayList<>();
        LambdaQueryWrapper<BudgetTarget> qw = new LambdaQueryWrapper<>();
        qw.eq(BudgetTarget::getPlanId, planId)
            .eq(BudgetTarget::getDeptId, deptId)
            .eq(BudgetTarget::getVersionNo, v)
            .eq(BudgetTarget::getStatus, PUBLISHED);
        if (StrUtil.isNotBlank(templateCode)) qw.eq(BudgetTarget::getTemplateCode, templateCode);
        qw.orderByAsc(BudgetTarget::getTemplateCode).orderByAsc(BudgetTarget::getItemOrder);
        return targetMapper.selectList(qw).stream().map(this::toRow).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> compareFill(Long planId, Long deptId, String templateCode) {
        List<Map<String, Object>> targets = getMyTargets(planId, deptId, templateCode);
        if (targets.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("rows", new ArrayList<>());
            empty.put("diffCount", 0);
            empty.put("hasTarget", false);
            return empty;
        }
        LambdaQueryWrapper<BudgetData> qw = new LambdaQueryWrapper<>();
        qw.eq(BudgetData::getPlanId, planId).eq(BudgetData::getDeptId, deptId);
        if (StrUtil.isNotBlank(templateCode)) qw.eq(BudgetData::getTemplateCode, templateCode);
        List<BudgetData> fillRows = budgetDataMapper.selectList(qw);
        Map<String, BigDecimal> fillMap = fillRows.stream()
            .filter(d -> d.getBudgetAmount() != null)
            .collect(Collectors.toMap(d -> d.getTemplateCode() + "_" + d.getItemCode(),
                BudgetData::getBudgetAmount, (a, b) -> a));

        List<Map<String, Object>> rows = new ArrayList<>();
        int diffCount = 0;
        for (Map<String, Object> t : targets) {
            Map<String, Object> row = new LinkedHashMap<>(t);
            String key = t.get("templateCode") + "_" + t.get("itemCode");
            BigDecimal fill = fillMap.get(key);
            row.put("fillAmount", fill);
            BigDecimal target = (BigDecimal) t.get("targetAmount");
            BigDecimal diffRate = null;
            String level = "OK";
            if (target != null && target.compareTo(BigDecimal.ZERO) != 0 && fill != null) {
                diffRate = fill.subtract(target).divide(target, 4, BigDecimal.ROUND_HALF_UP);
                BigDecimal min = (BigDecimal) t.get("toleranceMin");
                BigDecimal max = (BigDecimal) t.get("toleranceMax");
                if (min != null && diffRate.compareTo(min) < 0) level = "BELOW";
                else if (max != null && diffRate.compareTo(max) > 0) level = "ABOVE";
                else level = "OK";
            }
            row.put("diffRate", diffRate);
            row.put("level", level);
            if (!"OK".equals(level)) diffCount++;
            rows.add(row);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("rows", rows);
        result.put("diffCount", diffCount);
        result.put("hasTarget", true);
        return result;
    }

    private Map<String, Object> toRow(BudgetTarget t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("planId", t.getPlanId());
        m.put("deptId", t.getDeptId());
        m.put("templateCode", t.getTemplateCode());
        m.put("itemCode", t.getItemCode());
        m.put("itemName", t.getItemName());
        m.put("targetAmount", t.getTargetAmount());
        m.put("toleranceMin", t.getToleranceMin());
        m.put("toleranceMax", t.getToleranceMax());
        m.put("versionNo", t.getVersionNo());
        m.put("status", t.getStatus());
        m.put("itemOrder", t.getItemOrder());
        return m;
    }

    private Integer maxVersion(Long planId, Long deptId) {
        QueryWrapper<BudgetTarget> qw = new QueryWrapper<>();
        qw.select("ifnull(max(version_no),null) as version_no")
            .eq("plan_id", planId).eq("dept_id", deptId);
        BudgetTarget m = targetMapper.selectOne(qw);
        return m == null ? null : m.getVersionNo();
    }

    private Integer maxVersionByStatus(Long planId, Long deptId, String status) {
        QueryWrapper<BudgetTarget> qw = new QueryWrapper<>();
        qw.select("ifnull(max(version_no),null) as version_no")
            .eq("plan_id", planId).eq("dept_id", deptId).eq("status", status);
        BudgetTarget m = targetMapper.selectOne(qw);
        return m == null ? null : m.getVersionNo();
    }
}