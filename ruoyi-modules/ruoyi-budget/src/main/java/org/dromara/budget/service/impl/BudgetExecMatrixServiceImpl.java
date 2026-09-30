package org.dromara.budget.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.BudgetData;
import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.budget.mapper.BudgetDataMapper;
import org.dromara.budget.mapper.BudgetTemplateItemMapper;
import org.dromara.budget.service.IBudgetExecMatrixService;
import org.dromara.budget.util.BudgetScopeUtil;
import org.dromara.system.domain.SysDept;
import org.dromara.system.mapper.SysDeptMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 预算执行情况（各单位执行矩阵） Service实现
 *
 * 核心逻辑：
 * 1. 查询指定模板的所有科目（行）
 * 2. 查询所有参与填报的单位（列）
 * 3. 查询各单位各科目的预算额与季度执行额（Q1~Q4之和），计算执行率
 * 4. 组装成矩阵：行=科目，列=单位，单元格={预算, 执行, 执行率}
 * 5. 汇总行（isSummary=1）执行/预算由其下明细子项递归汇总
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetExecMatrixServiceImpl implements IBudgetExecMatrixService {

    private final BudgetTemplateItemMapper templateItemMapper;
    private final BudgetDataMapper budgetDataMapper;
    private final SysDeptMapper sysDeptMapper;

    @Override
    public List<Map<String, Object>> getTemplates() {
        // 执行情况排除B01公司基本信息表（无执行数据）
        LambdaQueryWrapper<BudgetTemplateItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(BudgetTemplateItem::getTemplateCode, "B01");
        wrapper.orderByAsc(BudgetTemplateItem::getTemplateCode);
        List<BudgetTemplateItem> items = templateItemMapper.selectList(wrapper);

        Map<String, String> codeNameMap = new LinkedHashMap<>();
        for (BudgetTemplateItem item : items) {
            codeNameMap.putIfAbsent(item.getTemplateCode(), item.getTemplateName());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : codeNameMap.entrySet()) {
            Map<String, Object> map = new HashMap<>();
            map.put("code", entry.getKey());
            map.put("name", entry.getValue());
            result.add(map);
        }
        return result;
    }

    @Override
    public Map<String, Object> getExecutionMatrixData(Long planId, String templateCode, String quarter) {
        if (quarter == null || quarter.isBlank()) {
            quarter = "ALL";
        }
        // 1. 查询该模板的所有科目（按itemCode排序）
        LambdaQueryWrapper<BudgetTemplateItem> tplWrapper = new LambdaQueryWrapper<>();
        tplWrapper.eq(BudgetTemplateItem::getTemplateCode, templateCode);
        tplWrapper.orderByAsc(BudgetTemplateItem::getItemCode);
        List<BudgetTemplateItem> templateItems = templateItemMapper.selectList(tplWrapper);

        // 2. 查询所有参与填报的单位（集团下子公司 + 本部）
        List<SysDept> reportDepts = queryReportDepts();

        // 3. 查询该方案该模板下所有单位的预算数据
        LambdaQueryWrapper<BudgetData> dataWrapper = new LambdaQueryWrapper<>();
        dataWrapper.eq(BudgetData::getPlanId, planId);
        dataWrapper.eq(BudgetData::getTemplateCode, templateCode);
        List<BudgetData> dataList = budgetDataMapper.selectList(dataWrapper);

        Map<String, BudgetData> dataMap = new HashMap<>();
        for (BudgetData d : dataList) {
            dataMap.put(d.getDeptId() + "_" + d.getItemCode(), d);
        }

        // 4. 组装列定义
        List<Map<String, Object>> columns = new ArrayList<>();
        Map<String, Object> itemCol = new HashMap<>();
        itemCol.put("field", "itemName");
        itemCol.put("label", "预算项目");
        itemCol.put("fixed", "left");
        itemCol.put("minWidth", 240);
        columns.add(itemCol);

        for (SysDept dept : reportDepts) {
            Map<String, Object> col = new HashMap<>();
            col.put("field", "dept_" + dept.getDeptId());
            col.put("label", dept.getDeptName());
            col.put("minWidth", 170);
            columns.add(col);
        }

        Map<String, Object> totalCol = new HashMap<>();
        totalCol.put("field", "total");
        totalCol.put("label", "合计");
        totalCol.put("minWidth", 170);
        totalCol.put("fixed", "right");
        columns.add(totalCol);

        // 5. 构建科目层级（parent_code -> children），用于汇总行递归
        Map<String, List<BudgetTemplateItem>> childrenMap = new HashMap<>();
        List<BudgetTemplateItem> rootParents = new ArrayList<>();
        BudgetTemplateItem totalRow = null;
        for (BudgetTemplateItem item : templateItems) {
            String pCode = item.getParentCode();
            if (pCode != null && !pCode.isBlank()) {
                childrenMap.computeIfAbsent(pCode, k -> new ArrayList<>()).add(item);
            }
            if (item.getIsSummary() != null && item.getIsSummary() == 1) {
                if (item.getItemName() != null && item.getItemName().contains("合计")) {
                    totalRow = item;
                } else if (item.getItemLevel() != null && item.getItemLevel() == 1) {
                    rootParents.add(item);
                }
            }
        }
        if (totalRow != null && !rootParents.isEmpty()) {
            childrenMap.computeIfAbsent(totalRow.getItemCode(), k -> new ArrayList<>(rootParents));
        }

        // 6. 逐科目组装行
        List<Map<String, Object>> rows = new ArrayList<>();
        // 每个单位的底部合计（仅统计明细行），存的是累计预算/执行
        Map<String, BigDecimal> colBudgetTotals = new HashMap<>();
        Map<String, BigDecimal> colExecTotals = new HashMap<>();

        for (BudgetTemplateItem item : templateItems) {
            Map<String, Object> row = new HashMap<>();
            row.put("itemCode", item.getItemCode());
            row.put("itemName", item.getItemName());
            row.put("isSummary", item.getIsSummary());
            row.put("itemLevel", item.getItemLevel());
            row.put("parentCode", item.getParentCode() == null ? null : item.getParentCode());

            boolean isGroup = item.getIsSummary() != null && item.getIsSummary() == 1;

            BigDecimal rowBudget = BigDecimal.ZERO;
            BigDecimal rowExec = BigDecimal.ZERO;

            for (SysDept dept : reportDepts) {
                // 科目设置了"适用公司"且不包含该公司时，该列置空（显示 -），不参与本行列合计
                if (!BudgetScopeUtil.appliesTo(item.getOrgScope(), dept.getDeptId())) {
                    row.put("dept_" + dept.getDeptId(), null);
                    continue;
                }
                BigDecimal budget;
                BigDecimal exec;
                if (isGroup) {
                    budget = computeDeptBudget(dept.getDeptId(), item.getItemCode(), childrenMap, dataMap);
                    exec = computeDeptExec(dept.getDeptId(), item.getItemCode(), childrenMap, dataMap, quarter);
                } else {
                    BudgetData d = dataMap.get(dept.getDeptId() + "_" + item.getItemCode());
                    budget = d != null ? d.getBudgetAmount() : null;
                    exec = d != null ? calcExecution(d, quarter) : null;
                    if (!isGroup) {
                        if (budget != null) colBudgetTotals.merge("dept_" + dept.getDeptId(), budget, BigDecimal::add);
                        if (exec != null) colExecTotals.merge("dept_" + dept.getDeptId(), exec, BigDecimal::add);
                    }
                }

                row.put("dept_" + dept.getDeptId(), buildCell(budget, exec));

                if (budget != null) rowBudget = rowBudget.add(budget);
                if (exec != null) rowExec = rowExec.add(exec);
            }

            // 该行合计
            row.put("total", buildCell(rowBudget, rowExec));
            if (!isGroup) {
                colBudgetTotals.merge("total", rowBudget, BigDecimal::add);
                colExecTotals.merge("total", rowExec, BigDecimal::add);
            }

            rows.add(row);
        }

        // 7. 组装返回结果，将扁平行按 parentCode 组装成树（根：无父或父不在表内）
        Map<String, Map<String, Object>> byCode = new HashMap<>();
        for (Map<String, Object> r : rows) {
            byCode.put((String) r.get("itemCode"), r);
            r.put("children", null);
        }
        List<Map<String, Object>> treeRows = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String pc = (String) r.get("parentCode");
            Map<String, Object> parent = (pc == null || pc.isBlank()) ? null : byCode.get(pc);
            if (parent != null && parent != r) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
                if (children == null) {
                    children = new ArrayList<>();
                    parent.put("children", children);
                }
                children.add(r);
            } else {
                treeRows.add(r);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("columns", columns);
        result.put("rows", treeRows);
        result.put("columnTotals", buildColumnTotals(colBudgetTotals, colExecTotals, reportDepts));
        result.put("templateCode", templateCode);
        result.put("quarter", quarter);
        return result;
    }

    /** 构造某单位某科目的单元格 { budget, exec, rate }；预算或执行为空返回 null */
    private Map<String, Object> buildCell(BigDecimal budget, BigDecimal exec) {
        if (budget == null && exec == null) return null;
        Map<String, Object> cell = new HashMap<>();
        cell.put("budget", budget != null ? budget : BigDecimal.ZERO);
        cell.put("exec", exec != null ? exec : BigDecimal.ZERO);
        BigDecimal rate = BigDecimal.ZERO;
        if (budget != null && budget.compareTo(BigDecimal.ZERO) > 0 && exec != null) {
            rate = exec.multiply(new BigDecimal("100"))
                .divide(budget, 2, RoundingMode.HALF_UP);
        } else if (budget != null && budget.compareTo(BigDecimal.ZERO) > 0) {
            rate = BigDecimal.ZERO;
        } else {
            rate = null;
        }
        cell.put("rate", rate);
        return cell;
    }

    /** 底部单位合计行数值 {budget, exec, rate} */
    private Map<String, Object> buildColumnTotals(Map<String, BigDecimal> colBudgetTotals,
                                                  Map<String, BigDecimal> colExecTotals,
                                                  List<SysDept> reportDepts) {
        Map<String, Object> totals = new HashMap<>();
        for (SysDept dept : reportDepts) {
            String key = "dept_" + dept.getDeptId();
            totals.put(key, buildCell(colBudgetTotals.getOrDefault(key, BigDecimal.ZERO),
                colExecTotals.getOrDefault(key, BigDecimal.ZERO)));
        }
        totals.put("total", buildCell(colBudgetTotals.getOrDefault("total", BigDecimal.ZERO),
            colExecTotals.getOrDefault("total", BigDecimal.ZERO)));
        return totals;
    }

    /** 按季度维度计算某条预算数据的执行金额；ALL=Q1+Q2+Q3+Q4，否则取对应季度值 */
    private BigDecimal calcExecution(BudgetData d, String quarter) {
        BigDecimal q1 = d.getQ1Amount() != null ? d.getQ1Amount() : BigDecimal.ZERO;
        BigDecimal q2 = d.getQ2Amount() != null ? d.getQ2Amount() : BigDecimal.ZERO;
        BigDecimal q3 = d.getQ3Amount() != null ? d.getQ3Amount() : BigDecimal.ZERO;
        BigDecimal q4 = d.getQ4Amount() != null ? d.getQ4Amount() : BigDecimal.ZERO;
        switch (quarter == null ? "ALL" : quarter.toUpperCase()) {
            case "Q1": return q1;
            case "Q2": return q2;
            case "Q3": return q3;
            case "Q4": return q4;
            default: return q1.add(q2).add(q3).add(q4);
        }
    }

    /** 递归汇总某汇总行在指定单位的预算金额 */
    private BigDecimal computeDeptBudget(Object deptId, String itemCode,
                                         Map<String, List<BudgetTemplateItem>> childrenMap,
                                         Map<String, BudgetData> dataMap) {
        List<BudgetTemplateItem> children = childrenMap.get(itemCode);
        if (children == null || children.isEmpty()) {
            BudgetData self = dataMap.get(deptId + "_" + itemCode);
            return self != null && self.getBudgetAmount() != null ? self.getBudgetAmount() : BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BudgetTemplateItem child : children) {
            if (child.getIsSummary() != null && child.getIsSummary() == 1) {
                sum = sum.add(computeDeptBudget(deptId, child.getItemCode(), childrenMap, dataMap));
            } else {
                BudgetData d = dataMap.get(deptId + "_" + child.getItemCode());
                if (d != null && d.getBudgetAmount() != null) {
                    sum = sum.add(d.getBudgetAmount());
                }
            }
        }
        return sum;
    }

    /** 递归汇总某汇总行在指定单位的执行金额 */
    private BigDecimal computeDeptExec(Object deptId, String itemCode,
                                       Map<String, List<BudgetTemplateItem>> childrenMap,
                                       Map<String, BudgetData> dataMap,
                                       String quarter) {
        List<BudgetTemplateItem> children = childrenMap.get(itemCode);
        if (children == null || children.isEmpty()) {
            BudgetData self = dataMap.get(deptId + "_" + itemCode);
            return self != null ? calcExecution(self, quarter) : BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BudgetTemplateItem child : children) {
            if (child.getIsSummary() != null && child.getIsSummary() == 1) {
                sum = sum.add(computeDeptExec(deptId, child.getItemCode(), childrenMap, dataMap, quarter));
            } else {
                BudgetData d = dataMap.get(deptId + "_" + child.getItemCode());
                if (d != null) {
                    sum = sum.add(calcExecution(d, quarter));
                }
            }
        }
        return sum;
    }

    /** 查询参与填报的单位：集团下面所有子公司和本部（排除国资委与内设部门） */
    private List<SysDept> queryReportDepts() {
        List<SysDept> allDepts = sysDeptMapper.selectList(null);
        List<SysDept> reportDepts = allDepts.stream()
            .filter(d -> {
                if (d.getParentId() == null || d.getParentId() == 0) return false;
                SysDept parent = allDepts.stream()
                    .filter(p -> p.getDeptId().equals(d.getParentId()))
                    .findFirst().orElse(null);
                if (parent == null) return false;
                SysDept grandParent = allDepts.stream()
                    .filter(g -> g.getDeptId().equals(parent.getParentId()))
                    .findFirst().orElse(null);
                if (grandParent == null) return false;
                return grandParent.getParentId() != null && grandParent.getParentId() == 0;
            })
            .sorted(Comparator.comparing(SysDept::getOrderNum))
            .collect(Collectors.toList());

        if (reportDepts.isEmpty()) {
            reportDepts = allDepts.stream()
                .filter(d -> d.getParentId() != null && d.getParentId() != 0)
                .filter(d -> {
                    String category = d.getDeptCategory();
                    if (category != null && category.contains("D")) return false;
                    return true;
                })
                .sorted(Comparator.comparing(SysDept::getOrderNum))
                .collect(Collectors.toList());
        }
        return reportDepts;
    }
}
