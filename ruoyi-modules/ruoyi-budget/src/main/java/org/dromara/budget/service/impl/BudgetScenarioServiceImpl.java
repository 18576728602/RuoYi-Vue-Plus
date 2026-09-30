package org.dromara.budget.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.BudgetData;
import org.dromara.budget.domain.BudgetPlan;
import org.dromara.budget.domain.BudgetScenario;
import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.budget.domain.bo.BudgetScenarioBo;
import org.dromara.budget.domain.vo.BudgetScenarioResultVo;
import org.dromara.budget.domain.vo.BudgetScenarioVo;
import org.dromara.budget.domain.vo.BudgetSensitivityRow;
import org.dromara.budget.mapper.BudgetDataMapper;
import org.dromara.budget.mapper.BudgetPlanMapper;
import org.dromara.budget.mapper.BudgetScenarioMapper;
import org.dromara.budget.mapper.BudgetTemplateItemMapper;
import org.dromara.budget.service.IBudgetScenarioService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.domain.SysDept;
import org.dromara.system.mapper.SysDeptMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 情景模拟与敏感性分析 Service实现
 *
 * <p>口径说明（首版确定，可按实际科目调整关键词）：
 * 以「已审批通过」的预算数据为基准，用科目名称关键词将预算科目软归入
 * 「营业收入」与「成本费用」两大类；营业收入不可识别时，不输出误导性净利润，
 * 仅展示总量指标。各情景净利润模型（各费率均以占收入比口径）：
 *   净利润 = 收入·毛利率 − 收入·成本费用率 − 收入·融资成本率 + 收入·投资回报率
 * 敏感性分析对关键参数做 ±5% 扰动，观察净利润变化量并排序其影响程度。
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetScenarioServiceImpl implements IBudgetScenarioService {

    private final BudgetScenarioMapper scenarioMapper;
    private final BudgetDataMapper budgetDataMapper;
    private final BudgetTemplateItemMapper templateItemMapper;
    private final BudgetPlanMapper budgetPlanMapper;
    private final SysDeptMapper sysDeptMapper;

    /** 收入类科目的名称关键词 */
    private static final List<String> REVENUE_KEYWORDS = Arrays.asList("收入");
    /** 成本费用类科目的名称关键词（不含收入） */
    private static final List<String> EXPENSE_KEYWORDS = Arrays.asList("成本", "费用", "支出");

    @Override
    public List<BudgetScenarioVo> listScenarios(Long planId) {
        List<BudgetScenario> list = scenarioMapper.selectList(new LambdaQueryWrapper<BudgetScenario>()
            .eq(BudgetScenario::getPlanId, planId)
            .orderByDesc(BudgetScenario::getIsDefault)
            .orderByAsc(BudgetScenario::getId));
        return list.stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveScenario(BudgetScenarioBo bo) {
        BudgetScenario entity = buildEntity(bo);
        if (entity.getId() != null) {
            scenarioMapper.updateById(entity);
        } else {
            scenarioMapper.insert(entity);
        }
    }

    private BudgetScenario buildEntity(BudgetScenarioBo bo) {
        if (bo.getPlanId() == null) {
            throw new ServiceException("预算方案不能为空");
        }
        BudgetScenario entity = new BudgetScenario();
        entity.setId(bo.getId());
        entity.setPlanId(bo.getPlanId());
        entity.setScenarioType(bo.getScenarioType());
        entity.setScenarioName(StrUtil.blankToDefault(bo.getScenarioName(), bo.getScenarioType()));
        entity.setRevenueGrowth(bo.getRevenueGrowth() == null ? BigDecimal.ZERO : bo.getRevenueGrowth());
        entity.setGrossMargin(bo.getGrossMargin() == null ? BigDecimal.ZERO : bo.getGrossMargin());
        entity.setCostExpenseRate(bo.getCostExpenseRate() == null ? BigDecimal.ZERO : bo.getCostExpenseRate());
        entity.setFinancingRate(bo.getFinancingRate() == null ? BigDecimal.ZERO : bo.getFinancingRate());
        entity.setInvestReturnRate(bo.getInvestReturnRate() == null ? BigDecimal.ZERO : bo.getInvestReturnRate());
        entity.setIsDefault(bo.getIsDefault() == null ? 0 : bo.getIsDefault());
        entity.setRemark(bo.getRemark());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteScenario(Long id) {
        scenarioMapper.deleteById(id);
    }

    @Override
    public BudgetScenarioResultVo simulate(Long planId, Long orgId) {
        orgId = resolveScopeOrg(orgId);
        BudgetPlan plan = budgetPlanMapper.selectById(planId);
        if (plan == null) {
            throw new ServiceException("预算方案不存在");
        }
        SysDept dept = sysDeptMapper.selectById(orgId);

        BudgetScenarioResultVo vo = new BudgetScenarioResultVo();
        vo.setPlanId(planId);
        vo.setPlanName(plan.getPlanName());
        vo.setOrgId(orgId);
        vo.setOrgName(dept != null ? dept.getDeptName() : "");

        // 基准输入：本方案+单位 已审批预算
        BaseMetrics base = loadBase(planId, orgId);
        if (base.revenue.compareTo(BigDecimal.ZERO) <= 0) {
            return degradeVo(vo, base, planId);
        }

        // 情景列表（缺省时内置基准情景）
        List<ScenarioParam> scenarios = loadScenarios(planId, base);
        if (scenarios.isEmpty()) {
            scenarios.add(new ScenarioParam("BASE", "基准情景", 0, 0, 0, 0, 0, true));
        }

        // 指标维度
        List<String> indicators = Arrays.asList("营业收入", "营业成本", "毛利", "期间费用", "净利润");
        vo.setIndicators(indicators);

        List<BudgetScenarioResultVo.ScenarioMetricRow> metrics = new ArrayList<>();
        for (String ind : indicators) {
            BudgetScenarioResultVo.ScenarioMetricRow row = new BudgetScenarioResultVo.ScenarioMetricRow();
            row.setMetricName(ind);
            row.setValues(new ArrayList<>());
            metrics.add(row);
        }

        BudgetScenarioResultVo.ScenarioIndicatorSeries optSeries = null;
        BudgetScenarioResultVo.ScenarioIndicatorSeries baseSeries = null;
        for (ScenarioParam sp : scenarios) {
            double[] m = compute(sp, base);
            List<Double> vals = Arrays.asList(m[0], m[1], m[2], m[3], m[4]);
            BudgetScenarioResultVo.ScenarioIndicatorSeries series = new BudgetScenarioResultVo.ScenarioIndicatorSeries();
            series.setName(sp.name);
            series.setValues(vals);
            vo.getSeries().add(series);
            for (int i = 0; i < indicators.size(); i++) {
                BudgetScenarioResultVo.ScenarioMetricVal val = new BudgetScenarioResultVo.ScenarioMetricVal();
                val.setScenarioName(sp.name);
                val.setValue(vals.get(i));
                metrics.get(i).getValues().add(val);
            }
            // 记录乐观与基准用于瀑布图
            if ("OPTIMISTIC".equalsIgnoreCase(sp.type)) {
                optSeries = series;
            }
            if (sp.base) {
                baseSeries = series;
            }
        }
        vo.setMetrics(metrics);

        // 瀑布图：基准净利润 → 各因素调整(乐观 vs 基准) → 乐观净利润
        if (optSeries != null) {
            vo.setWaterfall(buildWaterfall(base, scenarios));
        }
        return vo;
    }

    @Override
    public List<BudgetSensitivityRow> sensitivity(Long planId, Long orgId) {
        orgId = resolveScopeOrg(orgId);
        BaseMetrics base = loadBase(planId, orgId);
        if (base.revenue.compareTo(BigDecimal.ZERO) <= 0) {
            // 无收入口径，仅给出总量敏感迹象，impact 按总量测算
            return degradeSensitivity(planId, orgId);
        }
        ScenarioParam sp = new ScenarioParam("BASE", "基准", 0, 0, 0, 0, 0, true);
        double baseProfit = netProfit(sp, base);

        List<BudgetSensitivityRow> rows = new ArrayList<>();
        double delta = 5d; // ±5 个百分点 / ±5%
        rows.add(makeRow("营业收入", "+5%", impact(() -> netProfit(withGrowth(sp, 5), base), baseProfit), baseProfit, planId));
        rows.add(makeRow("营业收入", "-5%", impact(() -> netProfit(withGrowth(sp, -5), base), baseProfit), baseProfit, planId));
        rows.add(makeRow("毛利率", "+5pct", impact(() -> netProfit(withMargin(sp, 5), base), baseProfit), baseProfit, planId));
        rows.add(makeRow("毛利率", "-5pct", impact(() -> netProfit(withMargin(sp, -5), base), baseProfit), baseProfit, planId));
        rows.add(makeRow("成本费用率", "+5pct", impact(() -> netProfit(withCost(sp, 5), base), baseProfit), baseProfit, planId));
        rows.add(makeRow("成本费用率", "-5pct", impact(() -> netProfit(withCost(sp, -5), base), baseProfit), baseProfit, planId));
        rows.add(makeRow("融资成本", "+5pct", impact(() -> netProfit(withFin(sp, 5), base), baseProfit), baseProfit, planId));
        rows.add(makeRow("融资成本", "-5pct", impact(() -> netProfit(withFin(sp, -5), base), baseProfit), baseProfit, planId));
        rows.add(makeRow("投资回报率", "+5pct", impact(() -> netProfit(withInvest(sp, 5), base), baseProfit), baseProfit, planId));
        rows.add(makeRow("投资回报率", "-5pct", impact(() -> netProfit(withInvest(sp, -5), base), baseProfit), baseProfit, planId));

        // 按影响绝对值排序，龙卷风顶到底
        rows.sort(Comparator.comparing(r -> r.getImpact().abs()));
        Collections.reverse(rows);
        return rows;
    }

    // ============ 计算模型 ============

    /** 关键指标：[0]营业收入 [1]营业成本 [2]毛利 [3]期间费用 [4]净利润 */
    private double[] compute(ScenarioParam sp, BaseMetrics base) {
        double r = base.revenue.doubleValue() * (1 + sp.growth / 100d);
        double cost = r * sp.margin / 100d;       // 营业成本 = 收入×(1-毛利率) -> 此处毛利口径展示 cost 为成本
        double gross = r * sp.margin / 100d;      // 毛利
        double expense = r * sp.cost / 100d;      // 期间费用
        double fin = r * sp.finance / 100d;       // 财务费用
        double invest = r * sp.invest / 100d;     // 投资收益
        double profit = gross - expense - fin + invest;
        return new double[]{r, cost, gross, expense, profit};
    }

    private double netProfit(ScenarioParam sp, BaseMetrics base) {
        double r = base.revenue.doubleValue() * (1 + sp.growth / 100d);
        return r * (sp.margin - sp.cost - sp.finance + sp.invest) / 100d;
    }

    private ScenarioParam withGrowth(ScenarioParam sp, double d) { ScenarioParam c = copy(sp); c.growth += d; return c; }
    private ScenarioParam withMargin(ScenarioParam sp, double d) { ScenarioParam c = copy(sp); c.margin += d; return c; }
    private ScenarioParam withCost(ScenarioParam sp, double d) { ScenarioParam c = copy(sp); c.cost += d; return c; }
    private ScenarioParam withFin(ScenarioParam sp, double d) { ScenarioParam c = copy(sp); c.finance += d; return c; }
    private ScenarioParam withInvest(ScenarioParam sp, double d) { ScenarioParam c = copy(sp); c.invest += d; return c; }

    private ScenarioParam copy(ScenarioParam src) {
        return new ScenarioParam(src.type, src.name, src.margin, src.cost, src.finance, src.invest, src.growth, src.base);
    }

    private BigDecimal impact(java.util.function.DoubleSupplier delta, double baseProfit) {
        return bd(delta.getAsDouble() - baseProfit);
    }

    private BudgetSensitivityRow makeRow(String factor, String range, BigDecimal impact, double baseProfit, Long planId) {
        BudgetSensitivityRow row = new BudgetSensitivityRow();
        row.setFactor(factor);
        row.setChangeRange(range);
        row.setImpact(impact);
        row.setDegree(degree(impact, baseProfit));
        return row;
    }

    private Integer degree(BigDecimal impact, double baseProfit) {
        double ratio = Math.abs(impact.doubleValue()) / (Math.abs(baseProfit) + 1e-6);
        if (ratio >= 0.15) return 5;
        if (ratio >= 0.08) return 4;
        if (ratio >= 0.04) return 3;
        if (ratio >= 0.01) return 2;
        return 1;
    }

    private List<BudgetScenarioResultVo.WaterfallItem> buildWaterfall(BaseMetrics base, List<ScenarioParam> scenarios) {
        // 以乐观 vs 基准 展示各因素贡献（若无乐观，用 sum 幅度最大的情景）
        ScenarioParam baseSc = new ScenarioParam("BASE", "基准", 0, 0, 0, 0, 0, true);
        ScenarioParam opt = scenarios.stream()
            .filter(s -> "OPTIMISTIC".equalsIgnoreCase(s.type) || "乐观".equals(s.name))
            .findFirst().orElse(null);
        if (opt == null) {
            opt = scenarios.stream().filter(s -> !s.base).max(Comparator.comparingDouble(s -> s.growth + s.margin)).orElse(null);
        }
        List<BudgetScenarioResultVo.WaterfallItem> items = new ArrayList<>();
        double baseProfit = netProfit(baseSc, base);
        items.add(item("基准净利润", baseProfit));
        if (opt != null) {
            double r = base.revenue.doubleValue();
            double incGrowth = opt.growth;
            double incM = (opt.margin) / 100d * r;
            double incCost = -(opt.cost) / 100d * r;
            double incFin = -opt.finance / 100d * r;
            double incInvest = opt.invest / 100d * r;
            items.add(item("收入增长影响", bd(incGrowth > 0 ? r * opt.growth / 100d * opt.margin / 100d : 0)));
            items.add(item("毛利率影响", bd(incM)));
            items.add(item("成本费用影响", bd(incCost)));
            items.add(item("融资成本影响", bd(incFin)));
            items.add(item("投资收益影响", bd(incInvest)));
            items.add(item("乐观情景净利润", bd(netProfit(opt, base))));
        }
        return items;
    }

    private BudgetScenarioResultVo.WaterfallItem item(String name, double value) {
        BudgetScenarioResultVo.WaterfallItem it = new BudgetScenarioResultVo.WaterfallItem();
        it.setName(name);
        it.setValue(value);
        return it;
    }

    private BudgetScenarioResultVo.WaterfallItem item(String name, BigDecimal value) {
        BudgetScenarioResultVo.WaterfallItem it = new BudgetScenarioResultVo.WaterfallItem();
        it.setName(name);
        it.setValue(value.doubleValue());
        return it;
    }

    // ============ 数据读取 ============

    /** 基准输入 */
    private BaseMetrics loadBase(Long planId, Long orgId) {
        List<BudgetData> dataList = budgetDataMapper.selectList(new LambdaQueryWrapper<BudgetData>()
            .eq(BudgetData::getPlanId, planId)
            .eq(BudgetData::getDeptId, orgId)
            .eq(BudgetData::getStatus, "APPROVED")
            .ne(BudgetData::getTemplateCode, "B01"));
        Map<String, String> nameMap = templateItemMapper.selectList(
                new LambdaQueryWrapper<BudgetTemplateItem>().eq(BudgetTemplateItem::getPlanId, planId))
            .stream().filter(t -> StrUtil.isNotBlank(t.getItemCode()))
            .collect(Collectors.toMap(BudgetTemplateItem::getItemCode, BudgetTemplateItem::getItemName, (a, b) -> a));

        BaseMetrics base = new BaseMetrics();
        base.total = BigDecimal.ZERO;
        base.revenue = BigDecimal.ZERO;
        base.expense = BigDecimal.ZERO;
        for (BudgetData d : dataList) {
            BigDecimal amt = nz(d.getBudgetAmount());
            base.total = base.total.add(amt);
            String name = nameMap.getOrDefault(d.getItemCode(), "");
            if (isRevenue(name)) {
                base.revenue = base.revenue.add(amt);
            } else if (isExpense(name)) {
                base.expense = base.expense.add(amt);
            }
        }
        return base;
    }

    private List<ScenarioParam> loadScenarios(Long planId, BaseMetrics base) {
        List<BudgetScenario> list = scenarioMapper.selectList(new LambdaQueryWrapper<BudgetScenario>()
            .eq(BudgetScenario::getPlanId, planId));
        List<ScenarioParam> out = new ArrayList<>();
        for (BudgetScenario s : list) {
            ScenarioParam p = new ScenarioParam(s.getScenarioType(), s.getScenarioName(),
                dbl(s.getGrossMargin()), dbl(s.getCostExpenseRate()), dbl(s.getFinancingRate()),
                dbl(s.getInvestReturnRate()), dbl(s.getRevenueGrowth()),
                s.getIsDefault() != null && s.getIsDefault() == 1);
            out.add(p);
        }
        return out;
    }

    private boolean isRevenue(String name) {
        return REVENUE_KEYWORDS.stream().anyMatch(name::contains);
    }

    private boolean isExpense(String name) {
        return EXPENSE_KEYWORDS.stream().anyMatch(name::contains) && !isRevenue(name);
    }

    private BigDecimal num(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private double dbl(BigDecimal v) {
        return v == null ? 0d : v.doubleValue();
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    /** 无营业收入口径时的降级展示：仅总量指标，不输出误导性净利润 */
    private BudgetScenarioResultVo degradeVo(BudgetScenarioResultVo vo, BaseMetrics base, Long planId) {
        vo.setIndicators(Arrays.asList("预算总额"));
        BudgetScenarioResultVo.ScenarioMetricRow row = new BudgetScenarioResultVo.ScenarioMetricRow();
        row.setMetricName("预算总额");
        BudgetScenarioResultVo.ScenarioMetricVal val = new BudgetScenarioResultVo.ScenarioMetricVal();
        val.setScenarioName("基准");
        val.setValue(base.total.doubleValue());
        row.setValues(Collections.singletonList(val));
        vo.setMetrics(Collections.singletonList(row));
        BudgetScenarioResultVo.ScenarioIndicatorSeries series = new BudgetScenarioResultVo.ScenarioIndicatorSeries();
        series.setName("基准");
        series.setValues(Collections.singletonList(base.total.doubleValue()));
        vo.setSeries(Collections.singletonList(series));
        return vo;
    }

    private List<BudgetSensitivityRow> degradeSensitivity(Long planId, Long orgId) {
        BaseMetrics base = loadBase(planId, orgId);
        double t = base.total.doubleValue();
        List<BudgetSensitivityRow> rows = new ArrayList<>();
        rows.add(makeRow("预算总额", "+5%", bd(t * 0.05), t, planId));
        rows.add(makeRow("预算总额", "-5%", bd(-t * 0.05), t, planId));
        return rows;
    }

    private BudgetScenarioVo toVo(BudgetScenario s) {
        BudgetScenarioVo vo = new BudgetScenarioVo();
        vo.setId(s.getId());
        vo.setPlanId(s.getPlanId());
        vo.setScenarioType(s.getScenarioType());
        vo.setScenarioName(s.getScenarioName());
        vo.setRevenueGrowth(s.getRevenueGrowth());
        vo.setGrossMargin(s.getGrossMargin());
        vo.setCostExpenseRate(s.getCostExpenseRate());
        vo.setFinancingRate(s.getFinancingRate());
        vo.setInvestReturnRate(s.getInvestReturnRate());
        vo.setIsDefault(s.getIsDefault());
        vo.setRemark(s.getRemark());
        return vo;
    }

    private boolean isGlobalUser() {
        if (LoginHelper.isSuperAdmin() || LoginHelper.isTenantAdmin()) {
            return true;
        }
        try {
            List<org.dromara.common.core.domain.dto.RoleDTO> roles = LoginHelper.getLoginUser().getRoles();
            if (roles != null) {
                return roles.stream().anyMatch(r -> "1".equals(r.getDataScope()));
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private Long resolveScopeOrg(Long orgId) {
        if (isGlobalUser()) {
            return orgId;
        }
        return LoginHelper.getDeptId();
    }

    /** 情景参数 */
    private static class ScenarioParam {
        final String type;
        final String name;
        double growth;   // 收入增长率%
        double margin;   // 毛利率%
        double cost;     // 成本费用率%
        double finance;  // 融资成本%
        double invest;   // 投资回报%
        final boolean base;

        ScenarioParam(String type, String name, double margin, double cost, double finance, double invest, double growth, boolean base) {
            this.type = type;
            this.name = name;
            this.margin = margin;
            this.cost = cost;
            this.finance = finance;
            this.invest = invest;
            this.growth = growth;
            this.base = base;
        }
    }

    private static class BaseMetrics {
        BigDecimal total;
        BigDecimal revenue;
        BigDecimal expense;

        // 占位避免编译告警（expense 保留用于后续扩展/口径细化）
        public BigDecimal getExpense() {
            return expense;
        }
    }
}