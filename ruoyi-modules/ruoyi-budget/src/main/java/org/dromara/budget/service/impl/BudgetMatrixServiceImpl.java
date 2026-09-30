package org.dromara.budget.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.BudgetData;
import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.budget.mapper.BudgetDataMapper;
import org.dromara.budget.mapper.BudgetTemplateItemMapper;
import org.dromara.budget.service.IBudgetMatrixService;
import org.dromara.budget.util.BudgetTemplateTypeUtil;
import org.dromara.system.domain.SysDept;
import org.dromara.system.mapper.SysDeptMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 预算主表（各单位矩阵） Service实现
 *
 * 核心逻辑：
 * 1. 查询指定模板的所有科目（行）
 * 2. 查询所有参与填报的单位（列）
 * 3. 查询各单位各科目的预算数据
 * 4. 组装成矩阵：行=科目，列=单位，单元格=预算金额
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetMatrixServiceImpl implements IBudgetMatrixService {

    private final BudgetTemplateItemMapper templateItemMapper;
    private final BudgetDataMapper budgetDataMapper;
    private final SysDeptMapper sysDeptMapper;

    // 01公司基本信息：仅金额类科目参与合计（注册资本/资产总额/负债总额/净资产）
    // 联系电话、员工人数等纯数字文本不参与合计
    private static final Set<String> BASIC_01_AMOUNT_CODES = new HashSet<>(Arrays.asList("0103", "0104", "0105", "0106"));

    @Override
    public List<Map<String, Object>> getTemplates() {
        LambdaQueryWrapper<BudgetTemplateItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(BudgetTemplateItem::getTemplateCode);
        List<BudgetTemplateItem> items = templateItemMapper.selectList(wrapper);

        // 按templateCode去重
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
    public Map<String, Object> getMatrixData(Long planId, String templateCode) {
        // 1. 查询该模板的所有科目（按itemCode排序）
        LambdaQueryWrapper<BudgetTemplateItem> tplWrapper = new LambdaQueryWrapper<>();
        tplWrapper.eq(BudgetTemplateItem::getTemplateCode, templateCode);
        tplWrapper.orderByAsc(BudgetTemplateItem::getItemCode);
        List<BudgetTemplateItem> templateItems = templateItemMapper.selectList(tplWrapper);

        // 2. 查询所有参与填报的单位（排除顶级部门parent_id=0，排除国资委）
        List<SysDept> allDepts = sysDeptMapper.selectList(null);
        // 找出集团下面所有子公司和本部
        List<SysDept> reportDepts = allDepts.stream()
            .filter(d -> {
                // 排除国资委（parent_id=0的）
                if (d.getParentId() == null || d.getParentId() == 0) return false;
                // 排除内设部门（它们的parent是本部，本部的parent是集团）
                // 只保留直接挂在集团下的子公司和本部
                SysDept parent = allDepts.stream()
                    .filter(p -> p.getDeptId().equals(d.getParentId()))
                    .findFirst().orElse(null);
                if (parent == null) return false;
                // parent是集团（集团的parent是国资委）
                SysDept grandParent = allDepts.stream()
                    .filter(g -> g.getDeptId().equals(parent.getParentId()))
                    .findFirst().orElse(null);
                if (grandParent == null) return false;
                // 集团的parent是国资委（parent_id=0）
                return grandParent.getParentId() != null && grandParent.getParentId() == 0;
            })
            .sorted(Comparator.comparing(SysDept::getOrderNum))
            .collect(Collectors.toList());

        // 如果没有找到符合条件的，退而求其次取所有非顶级部门
        if (reportDepts.isEmpty()) {
            reportDepts = allDepts.stream()
                .filter(d -> d.getParentId() != null && d.getParentId() != 0)
                .filter(d -> {
                    // 排除内设部门（编码含D）
                    String category = d.getDeptCategory();
                    if (category != null && category.contains("D")) return false;
                    return true;
                })
                .sorted(Comparator.comparing(SysDept::getOrderNum))
                .collect(Collectors.toList());
        }

        // 3. 查询该方案该模板下所有单位的预算数据
        LambdaQueryWrapper<BudgetData> dataWrapper = new LambdaQueryWrapper<>();
        dataWrapper.eq(BudgetData::getPlanId, planId);
        dataWrapper.eq(BudgetData::getTemplateCode, templateCode);
        List<BudgetData> dataList = budgetDataMapper.selectList(dataWrapper);

        // 按 deptId + itemCode 分组
        Map<String, BudgetData> dataMap = new HashMap<>();
        for (BudgetData d : dataList) {
            String key = d.getDeptId() + "_" + d.getItemCode();
            dataMap.put(key, d);
        }

        // 4. 组装列定义（单位列表）
        List<Map<String, Object>> columns = new ArrayList<>();
        // 第一列固定为科目信息
        Map<String, Object> itemCol = new HashMap<>();
        itemCol.put("field", "itemName");
        itemCol.put("label", "预算项目");
        itemCol.put("fixed", "left");
        itemCol.put("minWidth", 220);
        columns.add(itemCol);

        // 每个单位一列
        for (SysDept dept : reportDepts) {
            Map<String, Object> col = new HashMap<>();
            col.put("field", "dept_" + dept.getDeptId());
            col.put("label", dept.getDeptName());
            col.put("minWidth", 150);
            col.put("align", "right");
            columns.add(col);
        }

        // 最后一列：合计
        // 每个模板都有合计列，放在右侧
        Map<String, Object> totalCol = new HashMap<>();
        totalCol.put("field", "total");
        totalCol.put("label", "合计");
        totalCol.put("minWidth", 140);
        totalCol.put("align", "right");
        totalCol.put("fixed", "right");
        columns.add(totalCol);

        // 5. 组装行数据
        // 5.1 构建科目层级：parent_code -> children
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
        // 总合计行（如"管理费用合计"）：将其下一级分类行作为children，保证自动汇总
        if (totalRow != null && !rootParents.isEmpty()) {
            childrenMap.computeIfAbsent(totalRow.getItemCode(), k -> new ArrayList<>(rootParents));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, BigDecimal> columnTotals = new HashMap<>(); // 每个单位的合计（仅统计明细行）

        for (BudgetTemplateItem item : templateItems) {
            Map<String, Object> row = new HashMap<>();
            row.put("itemCode", item.getItemCode());
            row.put("itemName", item.getItemName());
            row.put("isSummary", item.getIsSummary());
            row.put("itemLevel", item.getItemLevel());

            BigDecimal rowTotal = BigDecimal.ZERO;
            boolean anyAmount = false;

            for (SysDept dept : reportDepts) {
                if (BudgetTemplateTypeUtil.isText(templateCode)) {
                    // 01文本模板：仅金额类科目(0103~0106)参与合计，联系电话/员工人数等文本不参与累计
                    BudgetData d = dataMap.get(dept.getDeptId() + "_" + item.getItemCode());
                    String remark = d != null ? d.getRemark() : null;
                    row.put("dept_" + dept.getDeptId(), remark != null ? remark : "-");
                    if (remark != null && !remark.isBlank() && BASIC_01_AMOUNT_CODES.contains(item.getItemCode())) {
                        try {
                            BigDecimal numeric = new BigDecimal(remark.trim());
                            rowTotal = rowTotal.add(numeric);
                            anyAmount = true;
                        } catch (NumberFormatException ignored) {
                            // 非数字文本，不参与合计
                        }
                    }
                    continue;
                }

                boolean isGroup = item.getIsSummary() != null && item.getIsSummary() == 1;
                BigDecimal amount;
                if (isGroup) {
                    // 汇总行：自动汇总其下所有明细子项
                    amount = computeDeptAmount(dept.getDeptId(), item.getItemCode(), childrenMap, dataMap);
                } else {
                    BudgetData d = dataMap.get(dept.getDeptId() + "_" + item.getItemCode());
                    amount = d != null ? d.getBudgetAmount() : null;
                }
                if (amount != null) {
                    row.put("dept_" + dept.getDeptId(), amount);
                    rowTotal = rowTotal.add(amount);
                    anyAmount = true;
                    if (!isGroup) {
                        // 底部单位合计仅统计明细行，避免重复
                        columnTotals.merge("dept_" + dept.getDeptId(), amount, BigDecimal::add);
                    }
                } else {
                    row.put("dept_" + dept.getDeptId(), null);
                }
            }

            // 合计列：有数值则合计数值，否则文本显示"-"
            if (anyAmount) {
                row.put("total", rowTotal);
                columnTotals.merge("total", rowTotal, BigDecimal::add);
            } else {
                row.put("total", "-");
            }

            rows.add(row);
        }

        // 6. 组装返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("columns", columns);
        result.put("rows", rows);
        result.put("columnTotals", columnTotals);
        result.put("templateCode", templateCode);
        result.put("isTextTemplate", BudgetTemplateTypeUtil.isText(templateCode)); // 文本模板

        return result;
    }

    /**
     * 递归计算某父项（汇总行）在指定单位的金额 = 其下所有明细子项之和。
     * 无子项时取自身填报值，避免总合计行因缺少直接子项而算出0。
     */
    private BigDecimal computeDeptAmount(Object deptId, String itemCode,
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
                sum = sum.add(computeDeptAmount(deptId, child.getItemCode(), childrenMap, dataMap));
            } else {
                BudgetData d = dataMap.get(deptId + "_" + child.getItemCode());
                if (d != null && d.getBudgetAmount() != null) {
                    sum = sum.add(d.getBudgetAmount());
                }
            }
        }
        return sum;
    }
}
