package org.dromara.budget.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.BudgetData;
import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.budget.domain.bo.BudgetAnalysisQuery;
import org.dromara.budget.domain.vo.BudgetAnalysisOverview;
import org.dromara.budget.domain.vo.BudgetAnalysisResult;
import org.dromara.budget.domain.vo.BudgetAnalysisRow;
import org.dromara.budget.mapper.BudgetDataMapper;
import org.dromara.budget.mapper.BudgetTemplateItemMapper;
import org.dromara.budget.service.IBudgetAnalysisService;
import org.dromara.system.domain.SysDept;
import org.dromara.system.mapper.SysDeptMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashMap;

/**
 * 预算执行差异归因分析
 *
 * <p>按【单位】或【科目】维度对比 预算数 vs 执行数，逐维度汇总预算/执行/差异额，
 * 计算执行率、差异率与贡献度（|差异额|占比），定位差异主因（超支 Top / 结余 Top）。
 * 参考用友BIP 预算执行分析：维度下钻 + 异动贡献度排行。
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetAnalysisServiceImpl implements IBudgetAnalysisService {

    private final BudgetDataMapper budgetDataMapper;
    private final BudgetTemplateItemMapper templateItemMapper;
    private final SysDeptMapper sysDeptMapper;

    @Override
    public BudgetAnalysisOverview overview(BudgetAnalysisQuery q) {
        List<BudgetData> list = loadApproved(q);
        BigDecimal budgetTotal = BigDecimal.ZERO;
        BigDecimal execTotal = BigDecimal.ZERO;
        for (BudgetData d : list) {
            BigDecimal b = nvl(d.getBudgetAmount());
            BigDecimal e = sumExec(d);
            budgetTotal = budgetTotal.add(b);
            execTotal = execTotal.add(e);
        }
        BudgetAnalysisOverview o = new BudgetAnalysisOverview();
        o.setBudgetTotal(budgetTotal);
        o.setExecTotal(execTotal);
        o.setDiffTotal(budgetTotal.subtract(execTotal));
        o.setDiffRate(percent(budgetTotal.subtract(execTotal), budgetTotal));
        o.setExecRate(execTotal.signum() == 0 ? BigDecimal.ZERO : percent(execTotal, budgetTotal));
        o.setOrgCount(list.stream().map(BudgetData::getDeptId).filter(Objects::nonNull).distinct().count());
        o.setItemCount(list.stream().map(BudgetData::getItemCode).filter(Objects::nonNull).distinct().count());
        return o;
    }

    @Override
    public BudgetAnalysisResult analyze(BudgetAnalysisQuery q) {
        List<BudgetData> list = loadApproved(q);
        Map<String, BudgetTemplateItem> tplMap = loadTemplateIndex();

        Map<String, List<BudgetData>> groups = new LinkedHashMap<>();
        boolean byItem = "ITEM".equalsIgnoreCase(q.getDimension());
        for (BudgetData d : list) {
            String key = byItem ? d.getItemCode() : String.valueOf(d.getDeptId());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }

        List<BudgetAnalysisRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<BudgetData>> e : groups.entrySet()) {
            String key = e.getKey();
            List<BudgetData> items = e.getValue();
            BudgetAnalysisRow row = buildRow(q, key, byItem, items, tplMap);
            rows.add(row);
        }

        // 贡献度 = |差异额| / 总|差异额|
        BigDecimal totalAbsDiff = rows.stream()
            .map(BudgetAnalysisRow::getDiff)
            .map(this::abs)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        for (BudgetAnalysisRow r : rows) {
            r.setContribution(percent(abs(r.getDiff()), totalAbsDiff));
        }

        // 按差异额贡献排序：先展示超支(负差异绝对值大)的归因主因
        rows.sort(Comparator.comparing((BudgetAnalysisRow r) -> abs(r.getDiff())).reversed());

        BudgetAnalysisResult res = new BudgetAnalysisResult();
        res.setDimension(byItem ? "ITEM" : "UNIT");
        res.setPlanId(q.getPlanId());
        res.setOverview(overview(q));
        res.setRows(rows);
        return res;
    }

    private BudgetAnalysisRow buildRow(BudgetAnalysisQuery q, String key, boolean byItem,
                                       List<BudgetData> items, Map<String, BudgetTemplateItem> tplMap) {
        BudgetAnalysisRow row = new BudgetAnalysisRow();
        BigDecimal budget = items.stream().map(BudgetData::getBudgetAmount).map(this::nvl)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal exec = items.stream().map(this::sumExec)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        row.setKey(key);
        BigDecimal diff = budget.subtract(exec);
        row.setBudget(budget);
        row.setExec(exec);
        row.setDiff(diff);
        row.setDiffRate(percent(diff, budget));
        row.setExecRate(exec.signum() == 0 ? BigDecimal.ZERO : percent(exec, budget));
        row.setOverrun(exec.compareTo(budget) > 0);
        if (byItem) {
            row.setCode(key);
            BudgetTemplateItem it = tplMap.get(key);
            row.setName(it != null ? it.getItemName() : key);
            if (q.getTemplateCode() == null && it != null) {
                row.setTemplateCode(it.getTemplateCode());
                row.setTemplateName(it.getTemplateName());
            }
        } else {
            // 按单位聚合时，若筛选了单表则记录模板信息
            row.setCode(null);
            SysDept d = sysDeptMapper.selectById(Long.valueOf(key));
            row.setName(d != null ? d.getDeptName() : key);
        }
        return row;
    }

    private List<BudgetData> loadApproved(BudgetAnalysisQuery q) {
        LambdaQueryWrapper<BudgetData> w = new LambdaQueryWrapper<>();
        w.eq(BudgetData::getPlanId, q.getPlanId());
        w.eq(BudgetData::getStatus, "APPROVED");
        if (q.getTemplateCode() != null && !q.getTemplateCode().isEmpty()) {
            w.eq(BudgetData::getTemplateCode, q.getTemplateCode());
        }
        if (q.getOrgId() != null) {
            w.eq(BudgetData::getDeptId, q.getOrgId());
        }
        w.ne(BudgetData::getTemplateCode, "B01");
        return budgetDataMapper.selectList(w);
    }

    private Map<String, BudgetTemplateItem> loadTemplateIndex() {
        List<BudgetTemplateItem> items = templateItemMapper.selectList(
            new LambdaQueryWrapper<BudgetTemplateItem>().eq(BudgetTemplateItem::getPlanId, 0L));
        Map<String, BudgetTemplateItem> m = new HashMap<>();
        for (BudgetTemplateItem it : items) {
            m.putIfAbsent(it.getItemCode(), it);
        }
        return m;
    }

    private BigDecimal sumExec(BudgetData d) {
        BigDecimal q1 = nvl(d.getQ1Amount());
        BigDecimal q2 = nvl(d.getQ2Amount());
        BigDecimal q3 = nvl(d.getQ3Amount());
        BigDecimal q4 = nvl(d.getQ4Amount());
        BigDecimal sum = q1.add(q2).add(q3).add(q4);
        if (sum.signum() == 0 && d.getExecutionAmount() != null) {
            return d.getExecutionAmount();
        }
        return sum;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal abs(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.abs();
    }

    private BigDecimal percent(BigDecimal change, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return change.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }
}