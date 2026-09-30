package org.dromara.budget.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.BudgetData;
import org.dromara.budget.domain.BudgetDataVersion;
import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.budget.mapper.BudgetDataMapper;
import org.dromara.budget.mapper.BudgetDataVersionMapper;
import org.dromara.budget.mapper.BudgetTemplateItemMapper;
import org.dromara.budget.service.IBudgetDataVersionService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 提交版本快照 Service实现
 *
 * <p>PRD 7.2.1 / 两上两下：每次提交审批生成不可变版本快照。</p>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetDataVersionServiceImpl implements IBudgetDataVersionService {

    private final BudgetDataMapper budgetDataMapper;
    private final BudgetDataVersionMapper versionMapper;
    private final BudgetTemplateItemMapper budgetTemplateItemMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int snapshot(Long planId, Long deptId, String templateCode) {
        if (planId == null || deptId == null) {
            throw new org.dromara.common.core.exception.ServiceException("方案与填报单位缺失，无法生成版本快照");
        }
        LambdaQueryWrapper<BudgetData> qw = new LambdaQueryWrapper<>();
        qw.eq(BudgetData::getPlanId, planId)
            .eq(BudgetData::getDeptId, deptId)
            .eq(BudgetData::getStatus, "SUBMITTED");
        if (StrUtil.isNotBlank(templateCode)) {
            qw.eq(BudgetData::getTemplateCode, templateCode);
        }
        List<BudgetData> data = budgetDataMapper.selectList(qw);
        if (data.isEmpty()) {
            return 0;
        }

        // 科目名称索引：BudgetData 未存名称，从方案科目快照表补取（缺失回退科目编码）
        Set<String> codes = new HashSet<>();
        for (BudgetData d : data) {
            if (d.getItemCode() != null) codes.add(d.getItemCode());
        }
        Map<String, String> nameByCode = new HashMap<>();
        if (!codes.isEmpty()) {
            for (BudgetTemplateItem it : budgetTemplateItemMapper.selectList(new LambdaQueryWrapper<BudgetTemplateItem>()
                .eq(BudgetTemplateItem::getPlanId, planId)
                .in(BudgetTemplateItem::getItemCode, codes))) {
                nameByCode.putIfAbsent(it.getItemCode(), it.getItemName());
            }
        }

        Map<String, List<BudgetData>> byTpl = data.stream()
            .collect(Collectors.groupingBy(BudgetData::getTemplateCode));
        Long userId = LoginHelper.getUserId();
        String operator = LoginHelper.getUsername();
        Date now = new Date();
        int newestVersion = 0;

        for (Map.Entry<String, List<BudgetData>> e : byTpl.entrySet()) {
            String tpl = e.getKey();
            int next = nextVersion(planId, deptId, tpl);
            for (BudgetData d : e.getValue()) {
                BudgetDataVersion v = new BudgetDataVersion();
                v.setPlanId(planId);
                v.setDeptId(deptId);
                v.setTemplateCode(tpl);
                v.setVersionNo(next);
                v.setItemCode(d.getItemCode());
                v.setItemName(nameByCode.getOrDefault(d.getItemCode(), d.getItemCode()));
                v.setLastActual(d.getLastActual());
                v.setBudgetAmount(d.getBudgetAmount());
                v.setRemark(d.getRemark());
                v.setSubmittedBy(userId);
                v.setSubmittedName(operator);
                v.setSubmittedTime(now);
                versionMapper.insert(v);
            }
            newestVersion = Math.max(newestVersion, next);
        }
        return newestVersion;
    }

    private int nextVersion(Long planId, Long deptId, String templateCode) {
        QueryWrapper<BudgetDataVersion> qw = new QueryWrapper<>();
        qw.select("ifnull(max(version_no),0) as version_no")
            .eq("plan_id", planId)
            .eq("dept_id", deptId)
            .eq("template_code", templateCode);
        BudgetDataVersion m = versionMapper.selectOne(qw);
        return (m == null || m.getVersionNo() == null) ? 1 : m.getVersionNo() + 1;
    }

    @Override
    public int nextReleaseSeq(Long planId, Long deptId, String templateCode) {
        QueryWrapper<BudgetDataVersion> qw = new QueryWrapper<>();
        qw.select("ifnull(max(release_seq),0) as release_seq")
            .eq("plan_id", planId)
            .eq("dept_id", deptId)
            .eq("template_code", templateCode)
            .eq("release_type", "RELEASE");
        BudgetDataVersion m = versionMapper.selectOne(qw);
        return (m == null || m.getReleaseSeq() == null) ? 1 : m.getReleaseSeq() + 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int releaseSnapshot(Long planId, Long deptId, String templateCode, String releaseNo, int seq) {
        if (planId == null || deptId == null) {
            throw new org.dromara.common.core.exception.ServiceException("方案与填报单位缺失，无法生成正式发布快照");
        }
        LambdaQueryWrapper<BudgetData> qw = new LambdaQueryWrapper<>();
        qw.eq(BudgetData::getPlanId, planId)
            .eq(BudgetData::getDeptId, deptId)
            .eq(BudgetData::getStatus, "APPROVED");
        if (StrUtil.isNotBlank(templateCode)) {
            qw.eq(BudgetData::getTemplateCode, templateCode);
        }
        List<BudgetData> data = budgetDataMapper.selectList(qw);
        if (data.isEmpty()) {
            return 0;
        }

        // 科目名称索引（缺失回退科目编码）
        Set<String> codes = new HashSet<>();
        for (BudgetData d : data) {
            if (d.getItemCode() != null) codes.add(d.getItemCode());
        }
        Map<String, String> nameByCode = new HashMap<>();
        if (!codes.isEmpty()) {
            for (BudgetTemplateItem it : budgetTemplateItemMapper.selectList(new LambdaQueryWrapper<BudgetTemplateItem>()
                .eq(BudgetTemplateItem::getPlanId, planId)
                .in(BudgetTemplateItem::getItemCode, codes))) {
                nameByCode.putIfAbsent(it.getItemCode(), it.getItemName());
            }
        }

        Long userId = LoginHelper.getUserId();
        String operator = LoginHelper.getUsername();
        Date now = new Date();
        int basename = nextVersion(planId, deptId, StrUtil.blankToDefault(templateCode, "FULL"));
        int inserted = 0;
        for (BudgetData d : data) {
            BudgetDataVersion v = new BudgetDataVersion();
            v.setPlanId(planId);
            v.setDeptId(deptId);
            v.setTemplateCode(d.getTemplateCode());
            v.setVersionNo(basename);
            v.setItemCode(d.getItemCode());
            v.setItemName(nameByCode.getOrDefault(d.getItemCode(), d.getItemCode()));
            v.setLastActual(d.getLastActual());
            v.setBudgetAmount(d.getBudgetAmount());
            v.setRemark(d.getRemark());
            v.setSubmittedBy(userId);
            v.setSubmittedName(operator);
            v.setSubmittedTime(now);
            v.setReleaseType("RELEASE");
            v.setReleaseSeq(seq);
            v.setReleaseNo(releaseNo);
            versionMapper.insert(v);
            inserted++;
        }
        return inserted;
    }

    @Override
    public List<Map<String, Object>> listVersions(Long planId, Long deptId, String templateCode) {
        LambdaQueryWrapper<BudgetDataVersion> qw = new LambdaQueryWrapper<>();
        if (planId != null) qw.eq(BudgetDataVersion::getPlanId, planId);
        if (deptId != null) qw.eq(BudgetDataVersion::getDeptId, deptId);
        if (StrUtil.isNotBlank(templateCode)) qw.eq(BudgetDataVersion::getTemplateCode, templateCode);
        qw.orderByDesc(BudgetDataVersion::getVersionNo);
        List<BudgetDataVersion> rows = versionMapper.selectList(qw);

        Map<Integer, Map<String, Object>> map = new LinkedHashMap<>();

        // 查询模板科目，标记汇总行
        Set<String> summaryCodes = new HashSet<>();
        if (!rows.isEmpty()) {
            Set<String> allCodes = new HashSet<>();
            for (BudgetDataVersion r : rows) {
                if (r.getItemCode() != null) allCodes.add(r.getItemCode());
            }
            if (!allCodes.isEmpty() && rows.get(0).getPlanId() != null) {
                for (BudgetTemplateItem it : budgetTemplateItemMapper.selectList(new LambdaQueryWrapper<BudgetTemplateItem>()
                    .eq(BudgetTemplateItem::getPlanId, rows.get(0).getPlanId())
                    .in(BudgetTemplateItem::getItemCode, allCodes)
                    .eq(BudgetTemplateItem::getIsSummary, 1))) {
                    summaryCodes.add(it.getItemCode());
                }
            }
        }

        for (BudgetDataVersion r : rows) {
            // 跳过汇总行，避免科目数和预算合计重复计算
            if (summaryCodes.contains(r.getItemCode())) {
                continue;
            }
            map.computeIfAbsent(r.getVersionNo(), v -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("versionNo", r.getVersionNo());
                m.put("templateCode", r.getTemplateCode());
                m.put("submittedTime", r.getSubmittedTime());
                m.put("submittedName", r.getSubmittedName());
                m.put("itemCount", 0);
                m.put("totalAmount", new BigDecimal("0.00"));
                return m;
            });
            Map<String, Object> m = map.get(r.getVersionNo());
            m.put("itemCount", (Integer) m.get("itemCount") + 1);
            if (r.getBudgetAmount() != null) {
                m.put("totalAmount", ((BigDecimal) m.get("totalAmount")).add(r.getBudgetAmount()));
            }
        }
        return new ArrayList<>(map.values());
    }

    @Override
    public Map<String, Object> compareVersions(Long planId, Long deptId, String templateCode, int v1, int v2) {
        Map<String, Object> result = new HashMap<>();
        Map<String, BudgetDataVersion> a = loadVersion(planId, deptId, templateCode, v1);
        Map<String, BudgetDataVersion> b = loadVersion(planId, deptId, templateCode, v2);
        if (a.isEmpty() && b.isEmpty()) {
            throw new org.dromara.common.core.exception.ServiceException("所选版本无数据");
        }

        // 稳定顺序：以 v2（较新）顺序为主补 v1 独有项
        LinkedHashMap<String, BudgetDataVersion> ordered = new LinkedHashMap<>();
        if (!b.isEmpty()) {
            ordered.putAll(b);
        }
        for (Map.Entry<String, BudgetDataVersion> e : a.entrySet()) {
            ordered.putIfAbsent(e.getKey(), e.getValue());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int diffCount = 0;
        for (Map.Entry<String, BudgetDataVersion> e : ordered.entrySet()) {
            String code = e.getKey();
            BudgetDataVersion va = a.get(code);
            BudgetDataVersion vb = b.get(code);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("templateCode", vb != null ? vb.getTemplateCode() : va.getTemplateCode());
            row.put("itemCode", code);
            row.put("itemName", (vb != null ? vb.getItemName() : va.getItemName()));
            row.put("lastActualV1", va != null ? va.getLastActual() : null);
            row.put("lastActualV2", vb != null ? vb.getLastActual() : null);
            row.put("budgetV1", va != null ? va.getBudgetAmount() : null);
            row.put("budgetV2", vb != null ? vb.getBudgetAmount() : null);
            row.put("remarkV1", va != null ? va.getRemark() : null);
            row.put("remarkV2", vb != null ? vb.getRemark() : null);
            row.put("inV1", va != null);
            row.put("inV2", vb != null);
            boolean changed = budgetChanged(va, vb);
            row.put("changed", changed);
            if (changed) diffCount++;
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("diffCount", diffCount);
        result.put("totalRow", rows.size());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int restoreVersion(Long planId, Long deptId, String templateCode, int versionNo) {
        if (planId == null || deptId == null || versionNo <= 0) {
            throw new org.dromara.common.core.exception.ServiceException("恢复参数不完整（方案/单位/版本号）");
        }
        // 仅允许对草稿/未提交的数据进行恢复，避免覆盖已审批或已正式发布数据
        LambdaQueryWrapper<BudgetData> curWrap = new LambdaQueryWrapper<>();
        curWrap.eq(BudgetData::getPlanId, planId)
            .eq(BudgetData::getDeptId, deptId);
        if (StrUtil.isNotBlank(templateCode)) {
            curWrap.eq(BudgetData::getTemplateCode, templateCode);
        }
        curWrap.in(BudgetData::getStatus, "DRAFT", "SUBMITTED");
        List<BudgetData> current = budgetDataMapper.selectList(curWrap);
        for (BudgetData d : current) {
            if ("SUBMITTED".equals(d.getStatus())) {
                throw new org.dromara.common.core.exception.ServiceException(
                    "该范围已有待审批数据，请先撤回或待审批完成后，再恢复到历史版本");
            }
        }

        // 加载历史版本快照
        LambdaQueryWrapper<BudgetDataVersion> vw = new LambdaQueryWrapper<>();
        vw.eq(BudgetDataVersion::getPlanId, planId)
            .eq(BudgetDataVersion::getDeptId, deptId)
            .eq(BudgetDataVersion::getVersionNo, versionNo);
        if (StrUtil.isNotBlank(templateCode)) {
            vw.eq(BudgetDataVersion::getTemplateCode, templateCode);
        }
        List<BudgetDataVersion> snapshots = versionMapper.selectList(vw);
        if (snapshots.isEmpty()) {
            throw new org.dromara.common.core.exception.ServiceException("所选版本快照不存在");
        }

        // 按(模板-科目)建立快照映射，覆盖当前草稿数据
        Map<String, BudgetDataVersion> snapByKey = new HashMap<>();
        for (BudgetDataVersion s : snapshots) {
            snapByKey.put(s.getTemplateCode() + "-" + s.getItemCode(), s);
        }

        int updated = 0;
        for (BudgetData d : current) {
            BudgetDataVersion snap = snapByKey.get(d.getTemplateCode() + "-" + d.getItemCode());
            if (snap == null) {
                continue;
            }
            BudgetData update = new BudgetData();
            update.setId(d.getId());
            update.setLastActual(snap.getLastActual());
            update.setBudgetAmount(snap.getBudgetAmount());
            update.setRemark(snap.getRemark());
            // 重置为草稿，允许重新编辑提交
            update.setStatus("DRAFT");
            budgetDataMapper.updateById(update);
            updated++;
        }
        return updated;
    }

    private boolean budgetChanged(BudgetDataVersion a, BudgetDataVersion b) {
        if (a == null || b == null) return true;
        boolean same = eq(a.getLastActual(), b.getLastActual())
            && eq(a.getBudgetAmount(), b.getBudgetAmount())
            && java.util.Objects.equals(a.getRemark(), b.getRemark());
        return !same;
    }

    private boolean eq(BigDecimal x, BigDecimal y) {
        if (x == null && y == null) return true;
        if (x == null || y == null) return false;
        return x.compareTo(y) == 0;
    }

    private Map<String, BudgetDataVersion> loadVersion(Long planId, Long deptId, String templateCode, int versionNo) {
        LambdaQueryWrapper<BudgetDataVersion> qw = new LambdaQueryWrapper<>();
        qw.eq(BudgetDataVersion::getPlanId, planId)
            .eq(BudgetDataVersion::getDeptId, deptId)
            .eq(BudgetDataVersion::getVersionNo, versionNo);
        if (StrUtil.isNotBlank(templateCode)) {
            qw.eq(BudgetDataVersion::getTemplateCode, templateCode);
        }
        return versionMapper.selectList(qw).stream()
            .collect(Collectors.toMap(this::keyOf, r -> r, (x, y) -> x, LinkedHashMap::new));
    }

    private String keyOf(BudgetDataVersion r) {
        return (r.getTemplateCode() == null ? "" : r.getTemplateCode()) + "-" + r.getItemCode();
    }

    @Override
    public List<Map<String, Object>> versionDetail(Long planId, Long deptId, String templateCode, int versionNo) {
        LambdaQueryWrapper<BudgetDataVersion> qw = new LambdaQueryWrapper<>();
        qw.eq(BudgetDataVersion::getPlanId, planId)
            .eq(BudgetDataVersion::getDeptId, deptId)
            .eq(BudgetDataVersion::getVersionNo, versionNo);
        if (StrUtil.isNotBlank(templateCode)) {
            qw.eq(BudgetDataVersion::getTemplateCode, templateCode);
        }
        qw.orderByAsc(BudgetDataVersion::getTemplateCode).orderByAsc(BudgetDataVersion::getId);
        List<BudgetDataVersion> rows = versionMapper.selectList(qw);

        // 查询模板科目，标记汇总行和排序
        Map<String, Long> itemOrderMap = new HashMap<>();
        Set<String> summaryCodes = new HashSet<>();
        if (!rows.isEmpty()) {
            List<BudgetTemplateItem> tplItems = budgetTemplateItemMapper.selectList(new LambdaQueryWrapper<BudgetTemplateItem>()
                .eq(BudgetTemplateItem::getPlanId, planId));
            for (BudgetTemplateItem t : tplItems) {
                itemOrderMap.put(t.getTemplateCode() + "_" + t.getItemCode(), t.getItemOrder() != null ? t.getItemOrder() : 999L);
                if (t.getIsSummary() != null && t.getIsSummary() == 1) {
                    summaryCodes.add(t.getItemCode());
                }
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (BudgetDataVersion r : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemCode", r.getItemCode());
            row.put("itemName", r.getItemName());
            row.put("lastActual", r.getLastActual());
            row.put("budgetAmount", r.getBudgetAmount());
            row.put("remark", r.getRemark());
            row.put("isSummary", summaryCodes.contains(r.getItemCode()) ? 1 : 0);
            row.put("templateCode", r.getTemplateCode());
            row.put("versionNo", r.getVersionNo());
            row.put("submittedTime", r.getSubmittedTime());
            row.put("submittedName", r.getSubmittedName());
            result.add(row);
        }
        return result;
    }
}