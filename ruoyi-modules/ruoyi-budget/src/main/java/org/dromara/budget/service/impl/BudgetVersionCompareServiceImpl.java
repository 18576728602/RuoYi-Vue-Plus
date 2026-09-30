package org.dromara.budget.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.BudgetData;
import org.dromara.budget.domain.BudgetPlan;
import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.budget.domain.bo.BudgetVersionCompareQuery;
import org.dromara.budget.domain.vo.BudgetVersionCompareResult;
import org.dromara.budget.mapper.BudgetDataMapper;
import org.dromara.budget.mapper.BudgetPlanMapper;
import org.dromara.budget.mapper.BudgetTemplateItemMapper;
import org.dromara.budget.service.IBudgetVersionCompareService;
import org.dromara.system.domain.SysDept;
import org.dromara.system.mapper.SysDeptMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 预算版本对比实现
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetVersionCompareServiceImpl implements IBudgetVersionCompareService {

    private final BudgetDataMapper budgetDataMapper;
    private final BudgetPlanMapper budgetPlanMapper;
    private final BudgetTemplateItemMapper templateItemMapper;
    private final SysDeptMapper sysDeptMapper;

    @Override
    public BudgetVersionCompareResult compare(BudgetVersionCompareQuery q) {
        BudgetVersionCompareResult res = new BudgetVersionCompareResult();
        BudgetPlan planA = budgetPlanMapper.selectById(q.getPlanIdA());
        BudgetPlan planB = budgetPlanMapper.selectById(q.getPlanIdB());
        res.setPlanNameA(planA != null ? planA.getPlanName() : "版本A");
        res.setPlanNameB(planB != null ? planB.getPlanName() : "版本B");

        // 模板索引
        Map<String, BudgetTemplateItem> itemIndex = new HashMap<>();
        Map<String, String> tplName = new HashMap<>();
        for (BudgetTemplateItem it : templateItemMapper.selectList(new LambdaQueryWrapper<BudgetTemplateItem>()
            .eq(BudgetTemplateItem::getPlanId, 0L))) {
            itemIndex.putIfAbsent(it.getItemCode(), it);
            tplName.putIfAbsent(it.getTemplateCode(), it.getTemplateName());
        }

        // 单位名索引
        Map<Long, SysDept> deptName = new HashMap<>();
        for (SysDept d : sysDeptMapper.selectList(null)) {
            deptName.put(d.getDeptId(), d);
        }

        List<BudgetData> dataA = loadApproved(q.getPlanIdA(), q);
        List<BudgetData> dataB = loadApproved(q.getPlanIdB(), q);

        // 键：(orgId, templateCode, itemCode)
        Map<String, BigDecimal> mapA = index(dataA);
        Map<String, BigDecimal> mapB = index(dataB);
        Set<String> allKeys = new HashSet<>(mapA.keySet());
        allKeys.addAll(mapB.keySet());

        List<BudgetVersionCompareResult.CompareRow> rows = new ArrayList<>();
        BigDecimal changeTotal = BigDecimal.ZERO;
        long diffCount = 0;
        long upCount = 0;
        long downCount = 0;
        BigDecimal totalA = BigDecimal.ZERO;
        BigDecimal totalB = BigDecimal.ZERO;
        for (String key : allKeys) {
            String[] parts = key.split("#", 4);
            long orgId = Long.parseLong(parts[0]);
            String tpl = parts[1];
            String itemCode = parts[2];

            BigDecimal a = mapA.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal b = mapB.getOrDefault(key, BigDecimal.ZERO);
            totalA = totalA.add(a);
            totalB = totalB.add(b);

            BudgetVersionCompareResult.CompareRow row = new BudgetVersionCompareResult.CompareRow();
            row.setOrgId(orgId);
            SysDept d = deptName.get(orgId);
            String parentName = d != null && d.getParentId() != null
                && deptName.get(d.getParentId()) != null ? deptName.get(d.getParentId()).getDeptName() : null;
            row.setOrgName(d != null ? formatDept(d.getDeptName(), parentName) : String.valueOf(orgId));
            row.setTemplateCode(tpl);
            row.setTemplateName(tplName.getOrDefault(tpl, tpl));
            row.setItemCode(itemCode);
            row.setItemName(itemIndex.containsKey(itemCode) ? itemIndex.get(itemCode).getItemName() : itemCode);
            row.setAmountA(a);
            row.setAmountB(b);
            row.setDiff(b.subtract(a));
            row.setAdded(!mapA.containsKey(key) && mapB.containsKey(key));
            row.setRemoved(mapA.containsKey(key) && !mapB.containsKey(key));
            row.setChanged(!a.equals(b));

            if (!a.equals(b)) {
                diffCount++;
                changeTotal = changeTotal.add(a.subtract(b).abs());
                if (b.compareTo(a) > 0) upCount++;
                else downCount++;
            }
            row.setDiffRate(diffRate(a, b));
            rows.add(row);
        }

        rows.sort((x, y) -> {
            int cmp = x.getOrgName().compareTo(y.getOrgName());
            if (cmp != 0) return cmp;
            cmp = x.getTemplateCode().compareTo(y.getTemplateCode());
            if (cmp != 0) return cmp;
            return x.getItemCode().compareTo(y.getItemCode());
        });

        BudgetVersionCompareResult.CompareStat stat = new BudgetVersionCompareResult.CompareStat();
        stat.setTotalCount(allKeys.size());
        stat.setDiffCount(diffCount);
        stat.setUpCount(upCount);
        stat.setDownCount(downCount);
        stat.setChangeTotal(changeTotal);
        stat.setNetChange(totalB.subtract(totalA));

        res.setStat(stat);
        res.setRows(rows);
        res.setTotalA(totalA);
        res.setTotalB(totalB);
        res.setDiffTotal(totalB.subtract(totalA));
        return res;
    }

    private List<BudgetData> loadApproved(Long planId, BudgetVersionCompareQuery q) {
        LambdaQueryWrapper<BudgetData> w = new LambdaQueryWrapper<>();
        w.eq(BudgetData::getPlanId, planId);
        w.eq(BudgetData::getStatus, "APPROVED");
        w.ne(BudgetData::getTemplateCode, "B01");
        if (q.getOrgId() != null) {
            w.eq(BudgetData::getDeptId, q.getOrgId());
        }
        if (q.getTemplateCode() != null && !q.getTemplateCode().isEmpty()) {
            w.eq(BudgetData::getTemplateCode, q.getTemplateCode());
        }
        return budgetDataMapper.selectList(w);
    }

    private Map<String, BigDecimal> index(List<BudgetData> data) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        for (BudgetData d : data) {
            String key = d.getDeptId() + "#" + d.getTemplateCode() + "#" + d.getItemCode();
            m.merge(key, nvl(d.getBudgetAmount()), BigDecimal::add);
        }
        return m;
    }

    private BigDecimal diffRate(BigDecimal a, BigDecimal b) {
        BigDecimal base = a.signum() != 0 ? a.abs() : b.abs();
        if (base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return b.subtract(a).multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }

    private String formatDept(String deptName, String parentName) {
        if (deptName == null) return "未知单位";
        boolean hq = deptName.contains("（本部）") || deptName.contains("(本部)");
        if (hq) return "集团本部";
        if (parentName != null && (parentName.contains("（本部）") || parentName.contains("(本部)"))) {
            return "集团本部-" + deptName;
        }
        if (deptName.contains("公司")) {
            return deptName + "-财务部";
        }
        return deptName;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}