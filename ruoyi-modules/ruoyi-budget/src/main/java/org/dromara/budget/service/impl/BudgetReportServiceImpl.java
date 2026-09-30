package org.dromara.budget.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.BudgetData;
import org.dromara.budget.domain.BudgetPlan;
import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.budget.domain.bo.BudgetReportQuery;
import org.dromara.budget.domain.vo.BudgetReportVo;
import org.dromara.budget.mapper.BudgetDataMapper;
import org.dromara.budget.mapper.BudgetPlanMapper;
import org.dromara.budget.mapper.BudgetTemplateItemMapper;
import org.dromara.budget.service.IBudgetReportService;
import org.dromara.budget.util.BudgetUnitNameUtil;
import org.dromara.system.domain.SysDept;
import org.dromara.system.mapper.SysDeptMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 预算执行分析报告实现
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetReportServiceImpl implements IBudgetReportService {

    private final BudgetDataMapper budgetDataMapper;
    private final BudgetPlanMapper budgetPlanMapper;
    private final BudgetTemplateItemMapper templateItemMapper;
    private final SysDeptMapper sysDeptMapper;

    @Override
    public BudgetReportVo generate(BudgetReportQuery q) {
        if (q.getPlanId() == null) {
            throw new IllegalArgumentException("预算方案ID不能为空");
        }

        BudgetPlan plan = budgetPlanMapper.selectById(q.getPlanId());
        if (plan == null) {
            throw new IllegalArgumentException("预算方案不存在");
        }

        // 数据权限收窄：单位范围
        Long scopeOrg = q.getOrgId();

        // 取已审批执行数据
        LambdaQueryWrapper<BudgetData> dw = new LambdaQueryWrapper<>();
        dw.eq(BudgetData::getPlanId, q.getPlanId());
        dw.eq(BudgetData::getStatus, "APPROVED");
        if (scopeOrg != null) {
            dw.eq(BudgetData::getDeptId, scopeOrg);
        }
        dw.ne(BudgetData::getTemplateCode, "B01");
        List<BudgetData> data = budgetDataMapper.selectList(dw);

        // 模板科目索引(基础模板)
        Map<String, BudgetTemplateItem> itemIndex = new HashMap<>();
        Map<String, String> tplNameByCode = new LinkedHashMap<>();
        for (BudgetTemplateItem it : templateItemMapper.selectList(
            new LambdaQueryWrapper<BudgetTemplateItem>().eq(BudgetTemplateItem::getPlanId, 0L))) {
            itemIndex.putIfAbsent(it.getItemCode(), it);
            tplNameByCode.putIfAbsent(it.getTemplateCode(), it.getTemplateName());
        }

        // 单位名
        Map<Long, SysDept> deptMap = deptMap(scopeOrg);
        String orgName = scopeOrg != null && deptMap.get(scopeOrg) != null
            ? formatDept(deptMap.get(scopeOrg), deptMap)
            : null;

        BudgetReportVo vo = new BudgetReportVo();

        // ============ 封面 ============
        LocalDate now = LocalDate.now();
        String period = (q.getPeriod() != null && !q.getPeriod().isEmpty())
            ? q.getPeriod()
            : plan.getBudgetYear() + "年 " + now.getMonthValue() + "月";
        vo.setReportName("预算执行分析报告");
        vo.setReportPeriod(period);
        vo.setGenerateTime(new java.util.Date());
        vo.setPlanName(plan.getPlanName());
        vo.setOrgName(orgName);
        vo.setScopeLabel(scopeOrg == null ? "集团（全部填报单位）" : orgName);

        // ============ 执行概览 ============
        BigDecimal budgetTotal = BigDecimal.ZERO;
        BigDecimal execTotal = BigDecimal.ZERO;
        Set<Long> orgIds = new HashSet<>();
        Set<String> itemCodes = new HashSet<>();
        for (BudgetData d : data) {
            BigDecimal b = nvl(d.getBudgetAmount());
            budgetTotal = budgetTotal.add(b);
            execTotal = execTotal.add(sumExec(d));
            if (d.getDeptId() != null) orgIds.add(d.getDeptId());
            if (d.getItemCode() != null) itemCodes.add(d.getItemCode());
        }
        vo.setBudgetTotal(budgetTotal);
        vo.setExecTotal(execTotal);
        vo.setDiffTotal(budgetTotal.subtract(execTotal));
        vo.setDiffRate(percent(budgetTotal.subtract(execTotal), budgetTotal));
        vo.setExecRate(execTotal.signum() == 0 ? BigDecimal.ZERO : percent(execTotal, budgetTotal));
        vo.setOrgCount((long) orgIds.size());
        vo.setItemCount((long) itemCodes.size());

        // ============ 按表成本费用分析 ============
        Map<String, List<BudgetData>> byTpl = data.stream()
            .collect(Collectors.groupingBy(d -> String.valueOf(d.getTemplateCode()), LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> tableAnalysis = new ArrayList<>();
        for (Map.Entry<String, List<BudgetData>> e : byTpl.entrySet()) {
            String tpl = e.getKey();
            Map<String, Object> m = new LinkedHashMap<>();
            BigDecimal b = e.getValue().stream().map(BudgetData::getBudgetAmount).map(this::nvl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal x = e.getValue().stream().map(this::sumExec).reduce(BigDecimal.ZERO, BigDecimal::add);
            m.put("templateCode", tpl);
            m.put("templateName", tplNameByCode.getOrDefault(tpl, tpl));
            m.put("budget", b);
            m.put("exec", x);
            m.put("diff", b.subtract(x));
            m.put("execRate", x.signum() == 0 ? BigDecimal.ZERO : percent(x, b));
            long companyCount = e.getValue().stream().map(BudgetData::getDeptId).filter(Objects::nonNull).distinct().count();
            m.put("orgCount", companyCount);
            tableAnalysis.add(m);
        }
        tableAnalysis.sort(Comparator.comparing((Map<String, Object> m2) -> ((BigDecimal) m2.get("diff")).abs().doubleValue()).reversed());
        vo.setTableAnalysis(tableAnalysis);

        // ============ 季度趋势 ============
        vo.setQuarterTrend(quarterTrend(data, budgetTotal));

        // ============ 三级预警 + 超预算科目 ============
        int nowMonth = now.getMonthValue();
        BigDecimal timeRate = computeTimeRate(plan, nowMonth);
        List<Map<String, Object>> warns = new ArrayList<>();
        List<Map<String, Object>> overruns = new ArrayList<>();
        for (BudgetData d : data) {
            if (d.getItemCode() == null || d.getTemplateCode() == null) continue;
            BudgetTemplateItem it = itemIndex.get(d.getItemCode());
            if (it == null || (it.getIsSummary() != null && it.getIsSummary() == 1)) continue;
            BigDecimal budget = nvl(d.getBudgetAmount());
            if (budget.signum() <= 0) continue;
            BigDecimal exec = sumExec(d);
            if (exec.signum() <= 0) continue;
            BigDecimal rate = percent(exec, budget);

            // 超预算
            boolean overrun = exec.compareTo(budget) > 0;
            // 三级预警：红=执行率>=100；橙=>=90且超前时间进度；黄=>=80且超前时间进度
            boolean ahead = rate.compareTo(timeRate) > 0;
            String level = null;
            if (rate.compareTo(BigDecimal.valueOf(100)) >= 0) {
                level = "RED";
            } else if (rate.compareTo(BigDecimal.valueOf(90)) >= 0 && ahead) {
                level = "ORANGE";
            } else if (rate.compareTo(BigDecimal.valueOf(80)) >= 0 && ahead) {
                level = "YELLOW";
            }

            Map<String, Object> row = rowOf(d, it, tplNameByCode, deptMap, budget, exec, rate, timeRate);
            if (level != null) {
                row.put("level", level);
                warns.add(row);
            }
            if (overrun) {
                overruns.add(row);
            }
        }
        warns.sort((a, b) -> {
            int cmp = rank(a.get("level")).compareTo(rank(b.get("level")));
            return cmp != 0 ? cmp : Double.compare(Math.abs(NumberVal(b.get("diff"))), Math.abs(NumberVal(a.get("diff"))));
        });
        overruns.sort((a, b) -> Double.compare(Math.abs(NumberVal(b.get("diff"))), Math.abs(NumberVal(a.get("diff")))));

        vo.setWarningList(warns.size() > 10 ? warns.subList(0, 10) : warns);
        vo.setOverrunSubjects(overruns.size() > 10 ? overruns.subList(0, 10) : overruns);
        vo.setWarnTotal((long) warns.size());
        vo.setWarnRed(warns.stream().filter(m -> "RED".equals(m.get("level"))).count());
        vo.setWarnOrange(warns.stream().filter(m -> "ORANGE".equals(m.get("level"))).count());
        vo.setWarnYellow(warns.stream().filter(m -> "YELLOW".equals(m.get("level"))).count());

        // ============ 结论 / 关注 / 建议 ============
        buildNarrative(vo);

        return vo;
    }

    @Override
    public String generateWordHtml(BudgetReportQuery q) {
        BudgetReportVo vo = generate(q);
        StringBuilder sb = new StringBuilder(8192);
        sb.append("<html xmlns:w=\"urn:schemas-microsoft-com:office:word\" lang=\"zh-CN\"><head><meta charset=\"utf-8\">")
            .append("<style>")
            .append("body{font-family:'\u5b8b\u4f53',SimSun;font-size:12pt;color:#222;}")
            .append("h1{font-size:22pt;text-align:center;margin:40px 0 8px;}")
            .append(".sub{text-align:center;font-size:12pt;color:#555;margin-bottom:8px;}")
            .append(".meta{text-align:center;font-size:10.5pt;color:#666;margin:16px 0 30px;}")
            .append("h2{font-size:14pt;border-left:4px solid #409eff;padding-left:8px;margin:22px 0 10px;}")
            .append("table{border-collapse:collapse;width:100%;margin:8px 0;}th,td{border:1px solid #999;padding:4px 6px;font-size:10.5pt;}")
            .append("th{background:#f2f4f7;text-align:center;}td.num{text-align:right;}td.c{text-align:center;}")
            .append("ul,ol{margin:6px 0;padding-left:24px;}li{line-height:1.7;}")
            .append(".kv{width:100%;} .kv td{border:none;padding:2px 8px;}")
            .append(".rv{color:#c0392b;} .ok{color:#27ae60;}")
            .append("</style></head><body>");

        // 封面
        sb.append("<h1>").append(esc(vo.getReportName())).append("</h1>")
            .append("<div class=\"sub\">").append(esc(vo.getPlanName())).append("</div>")
            .append("<div class=\"meta\">报告期间：").append(esc(vo.getReportPeriod()))
            .append("&nbsp;&nbsp;统计范围：").append(esc(vo.getScopeLabel()))
            .append("&nbsp;&nbsp;生成时间：").append(vo.getGenerateTime() == null ? "-" : vo.getGenerateTime())
            .append("</div>");

        // 一、执行概览
        boolean over = vo.getDiffTotal() != null && vo.getDiffTotal().signum() < 0;
        sb.append("<h2>一、执行概览</h2>")
            .append("<table class=\"kv\">")
            .append("<tr><td>预算总额</td><td>").append(fmt(vo.getBudgetTotal())).append(" 万元</td>")
            .append("<td>累计执行</td><td>").append(fmt(vo.getExecTotal())).append(" 万元</td></tr>")
            .append("<tr><td>差异额</td><td class=\"").append(over ? "rv" : "ok").append("\">").append(fmt(vo.getDiffTotal())).append(" 万元</td>")
            .append("<td>整体执行率</td><td>").append(vo.getExecRate()).append("%</td></tr>")
            .append("</table>")
            .append("<p>共 ").append(vo.getOrgCount()).append(" 个填报单位 · ").append(vo.getItemCount())
            .append(" 个预算科目 · 预警 ").append(vo.getWarnTotal()).append(" 条（红 ")
            .append(vo.getWarnRed()).append(" / 橙 ").append(vo.getWarnOrange()).append(" / 黄 ").append(vo.getWarnYellow()).append("）</p>");
        if (vo.getConclusions() != null && !vo.getConclusions().isEmpty()) {
            sb.append("<ul>");
            for (String c : vo.getConclusions()) {
                sb.append("<li>").append(esc(c)).append("</li>");
            }
            sb.append("</ul>");
        }

        // 二、按表分析
        sb.append("<h2>二、按表成本费用分析</h2>")
            .append("<table><tr><th>预算表</th><th>预算(万元)</th><th>执行(万元)</th><th>差异额(万元)</th><th>执行率</th></tr>");
        for (Map<String, Object> m : vo.getTableAnalysis()) {
            sb.append("<tr><td>").append(esc(String.valueOf(m.get("templateName")))).append("</td>")
                .append("<td class=\"num\">").append(fmt((BigDecimal) m.get("budget"))).append("</td>")
                .append("<td class=\"num\">").append(fmt((BigDecimal) m.get("exec"))).append("</td>")
                .append("<td class=\"num\">").append(fmt((BigDecimal) m.get("diff"))).append("</td>")
                .append("<td class=\"c\">").append(m.get("execRate")).append("%</td></tr>");
        }
        sb.append("</table>");

        // 三、季度趋势
        sb.append("<h2>三、季度执行趋势</h2>")
            .append("<table><tr><th>季度</th><th>执行金额(万元)</th><th>占预算比例</th></tr>");
        for (Map<String, Object> qt : vo.getQuarterTrend()) {
            sb.append("<tr><td class=\"c\">").append(esc(String.valueOf(qt.get("label")))).append("</td>")
                .append("<td class=\"num\">").append(fmt((BigDecimal) qt.get("exec"))).append("</td>")
                .append("<td class=\"c\">").append(qt.get("rate")).append("%</td></tr>");
        }
        sb.append("</table>");

        // 四、预警事项
        sb.append("<h2>四、预警事项</h2>")
            .append("<table><tr><th>等级</th><th>单位</th><th>预算表/科目</th><th>预算(万元)</th><th>执行(万元)</th><th>执行率</th></tr>");
        for (Map<String, Object> w : vo.getWarningList()) {
            String lv = String.valueOf(w.get("level"));
            sb.append("<tr><td class=\"c\">").append("RED".equals(lv) ? "\u7ea2\u8272" : ("ORANGE".equals(lv) ? "\u6a59\u8272" : "\u9ec4\u8272"))
                .append("</td><td>").append(esc(String.valueOf(w.get("orgName")))).append("</td>")
                .append("<td>").append(esc(String.valueOf(w.get("templateName")))).append(" / ").append(esc(String.valueOf(w.get("itemName")))).append("</td>")
                .append("<td class=\"num\">").append(fmt((BigDecimal) w.get("budget"))).append("</td>")
                .append("<td class=\"num\">").append(fmt((BigDecimal) w.get("exec"))).append("</td>")
                .append("<td class=\"c\">").append(w.get("execRate")).append("%</td></tr>");
        }
        if (vo.getWarningList() == null || vo.getWarningList().isEmpty()) {
            sb.append("<tr><td colspan=\"6\" style=\"text-align:center;color:#888\">本期无超标预警</td></tr>");
        }
        sb.append("</table>");

        // 五、超预算科目
        sb.append("<h2>五、超预算科目（差异归因 Top）</h2>")
            .append("<table><tr><th>单位</th><th>预算表/科目</th><th>预算(万元)</th><th>执行(万元)</th><th>差异额(万元)</th><th>执行率</th></tr>");
        for (Map<String, Object> o : vo.getOverrunSubjects()) {
            sb.append("<tr><td>").append(esc(String.valueOf(o.get("orgName")))).append("</td>")
                .append("<td>").append(esc(String.valueOf(o.get("templateName")))).append(" / ").append(esc(String.valueOf(o.get("itemName")))).append("</td>")
                .append("<td class=\"num\">").append(fmt((BigDecimal) o.get("budget"))).append("</td>")
                .append("<td class=\"num\">").append(fmt((BigDecimal) o.get("exec"))).append("</td>")
                .append("<td class=\"num rv\">").append(fmt((BigDecimal) o.get("diff"))).append("</td>")
                .append("<td class=\"c\">").append(o.get("execRate")).append("%</td></tr>");
        }
        if (vo.getOverrunSubjects() == null || vo.getOverrunSubjects().isEmpty()) {
            sb.append("<tr><td colspan=\"6\" style=\"text-align:center;color:#888\">本期无超预算科目</td></tr>");
        }
        sb.append("</table>");

        // 六、重点关注
        sb.append("<h2>六、下月重点关注</h2><ol>");
        for (String f : vo.getFocusPoints()) {
            sb.append("<li>").append(esc(f)).append("</li>");
        }
        sb.append("</ol>");

        // 七、管理建议
        sb.append("<h2>七、管理建议</h2><ul>");
        for (String s : vo.getSuggestions()) {
            sb.append("<li>").append(esc(s)).append("</li>");
        }
        sb.append("</ul>");

        sb.append("<p style=\"margin-top:30px;text-align:center;color:#aaa;font-size:9pt;\">本报告由全面预算管理系统自动生成</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private Integer rank(Object level) {
        if ("RED".equals(level)) return 0;
        if ("ORANGE".equals(level)) return 1;
        if ("YELLOW".equals(level)) return 2;
        return 9;
    }

    private Map<String, Object> rowOf(BudgetData d, BudgetTemplateItem it, Map<String, String> tplName,
                                      Map<Long, SysDept> deptMap, BigDecimal budget,
                                      BigDecimal exec, BigDecimal rate, BigDecimal timeRate) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("templateCode", d.getTemplateCode());
        m.put("templateName", tplName.getOrDefault(d.getTemplateCode(), d.getTemplateCode()));
        m.put("orgId", d.getDeptId());
        m.put("orgName", d.getDeptId() != null && deptMap.get(d.getDeptId()) != null
            ? formatDept(deptMap.get(d.getDeptId()), deptMap) : "未知单位");
        m.put("itemCode", d.getItemCode());
        m.put("itemName", it != null ? it.getItemName() : d.getItemCode());
        m.put("budget", budget);
        m.put("exec", exec);
        m.put("diff", budget.subtract(exec));
        m.put("execRate", rate);
        m.put("timeRate", timeRate);
        return m;
    }

    private List<Map<String, Object>> quarterTrend(List<BudgetData> data, BigDecimal budgetTotal) {
        BigDecimal[] q = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (BudgetData d : data) {
            q[0] = q[0].add(nvl(d.getQ1Amount()));
            q[1] = q[1].add(nvl(d.getQ2Amount()));
            q[2] = q[2].add(nvl(d.getQ3Amount()));
            q[3] = q[3].add(nvl(d.getQ4Amount()));
        }
        BigDecimal legacy = data.stream().map(BudgetData::getExecutionAmount).filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (q[0].add(q[1]).add(q[2]).add(q[3]).signum() == 0 && legacy.signum() > 0) {
            q[0] = legacy;
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", "Q" + (i + 1));
            m.put("exec", q[i]);
            m.put("rate", budgetTotal.signum() > 0 ? percent(q[i], budgetTotal) : BigDecimal.ZERO);
            list.add(m);
        }
        return list;
    }

    private void buildNarrative(BudgetReportVo vo) {
        List<String> conclusions = new ArrayList<>();
        conclusions.add("本期（" + vo.getReportPeriod() + "）预算总额 " + fmt(vo.getBudgetTotal())
            + " 万元，累计执行 " + fmt(vo.getExecTotal()) + " 万元，整体执行率 " + vo.getExecRate() + "%。");
        if (vo.getDiffTotal().signum() >= 0) {
            conclusions.add("预算执行总体在可控范围内，结余 " + fmt(vo.getDiffTotal()) + " 万元。");
        } else {
            conclusions.add("当前存在超支，超支 " + fmt(vo.getDiffTotal().abs()) + " 万元，需重点关注超预算科目。");
        }
        if (vo.getWarnRed() != null && vo.getWarnRed() > 0) {
            conclusions.add("存在 " + vo.getWarnRed() + " 个红色（超预算）预警科目，已列入重点关注。");
        }
        conclusions.add("共涉及 " + vo.getOrgCount() + " 个填报单位、纳入 " + vo.getItemCount() + " 个预算科目。");
        vo.setConclusions(conclusions);

        List<String> focuses = new ArrayList<>();
        for (Map<String, Object> w : vo.getWarningList()) {
            focuses.add(w.get("orgName") + "·" + w.get("templateName") + "·" + w.get("itemName")
                + "（执行率 " + w.get("execRate") + "%）");
        }
        if (focuses.isEmpty()) {
            focuses.add("本期无超标预警科目，整体执行平稳。");
        }
        vo.setFocusPoints(focuses);

        List<String> suggestions = new ArrayList<>();
        if (vo.getWarnRed() != null && vo.getWarnRed() > 0) {
            suggestions.add("对红色超预算科目建议发起预算调整或费用压减，避免持续超支。");
        }
        if (vo.getExecRate() != null && vo.getExecRate().compareTo(new BigDecimal("85")) < 0) {
            suggestions.add("整体执行率偏低，需核实预算安排与现实执行节奏是否存在偏差。");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("维持当前预算执行节奏，持续监控执行率与时间进度的匹配情况。");
        }
        vo.setSuggestions(suggestions);
    }

    private BigDecimal computeTimeRate(BudgetPlan p, int nowMonth) {
        long year = p.getBudgetYear() != null ? p.getBudgetYear() : LocalDate.now().getYear();
        int curYear = LocalDate.now().getYear();
        if (year < curYear) return BigDecimal.valueOf(100);
        if (year > curYear) return BigDecimal.ZERO;
        if (nowMonth < 1) nowMonth = 1;
        if (nowMonth > 12) nowMonth = 12;
        return BigDecimal.valueOf(nowMonth * 100 / 12);
    }

    private Map<Long, SysDept> deptMap(Long focus) {
        Map<Long, SysDept> m = new HashMap<>();
        for (SysDept d : sysDeptMapper.selectList(null)) {
            m.put(d.getDeptId(), d);
        }
        return m;
    }

    private String formatDept(SysDept dept, Map<Long, SysDept> deptMap) {
        SysDept parent = dept.getParentId() != null ? deptMap.get(dept.getParentId()) : null;
        return BudgetUnitNameUtil.format(dept.getDeptName(), parent != null ? parent.getDeptName() : null);
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

    private BigDecimal percent(BigDecimal change, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return change.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }

    private double NumberVal(Object v) {
        try {
            return v == null ? 0d : new BigDecimal(String.valueOf(v)).doubleValue();
        } catch (Exception e) {
            return 0d;
        }
    }

    private String fmt(BigDecimal v) {
        return v == null ? "0.00" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}