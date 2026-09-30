package org.dromara.budget.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.*;
import org.dromara.budget.domain.bo.BudgetFillBo;
import org.dromara.budget.domain.bo.BudgetValidateQuery;
import org.dromara.budget.domain.vo.BudgetFillVo;
import org.dromara.budget.domain.vo.BudgetValidateResult;
import org.dromara.budget.mapper.*;
import org.dromara.budget.service.IBudgetApprovalChainService;
import org.dromara.budget.service.IBudgetDataVersionService;
import org.dromara.budget.service.IBudgetFillService;
import org.dromara.budget.service.IBudgetValidateService;
import org.dromara.budget.service.IBudgetGatherMapService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.budget.util.BudgetUnitNameUtil;
import org.dromara.budget.util.BudgetScopeUtil;
import org.dromara.system.mapper.SysDeptMapper;
import org.dromara.system.domain.SysDept;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 预算智能填报 Service实现
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetFillServiceImpl implements IBudgetFillService {

    private final BudgetDataMapper budgetDataMapper;
    private final BudgetTemplateItemMapper budgetTemplateItemMapper;
    private final DeptBudgetModuleMapper deptBudgetModuleMapper;
    private final BudgetPlanMapper budgetPlanMapper;
    private final BudgetOperationLogMapper budgetOperationLogMapper;
    private final SysDeptMapper sysDeptMapper;
    private final IBudgetDataVersionService budgetDataVersionService;
    private final IBudgetValidateService budgetValidateService;
    private final IBudgetApprovalChainService budgetApprovalChainService;
    private final IBudgetGatherMapService budgetGatherMapService;
    private final BudgetApprovalRecordMapper budgetApprovalRecordMapper;

    /**
     * 获取当前填报部门ID
     * admin用户没有部门，使用前端传入的deptId
     */
    private Long getCurrentDeptId(Long deptId) {
        if (deptId != null) {
            return deptId;
        }
        Long userDeptId = LoginHelper.getDeptId();
        if (userDeptId == null) {
            throw new ServiceException("无法获取部门信息，admin用户请选择填报单位");
        }
        return userDeptId;
    }

    /**
     * 获取当前登录人的填报单位（含格式化显示名）
     */
    public Map<String, Object> getMyUnit() {
        Map<String, Object> map = new HashMap<>();
        Long deptId = LoginHelper.getDeptId();
        SysDept dept = deptId == null ? null : sysDeptMapper.selectById(deptId);
        if (dept == null) {
            map.put("deptId", deptId);
            map.put("deptName", null);
            map.put("displayName", null);
            return map;
        }
        SysDept parent = dept.getParentId() == null ? null : sysDeptMapper.selectById(dept.getParentId());
        String parentName = parent == null ? null : parent.getDeptName();
        map.put("deptId", deptId);
        map.put("deptName", dept.getDeptName());
        map.put("parentId", dept.getParentId());
        map.put("displayName", BudgetUnitNameUtil.format(dept.getDeptName(), parentName));
        return map;
    }

    /**
     * 校验方案是否处于可填报状态（仅 PUBLISHED/ARCHIVED）
     */
    private void validatePlanOpen(Long planId) {
        BudgetPlan plan = planId == null ? null : budgetPlanMapper.selectById(planId);
        if (plan == null) {
            throw new ServiceException("预算方案不存在");
        }
        if (!"PUBLISHED".equals(plan.getStatus()) && !"ARCHIVED".equals(plan.getStatus())) {
            throw new ServiceException("当前预算方案为草稿/关闭状态，不允许填报");
        }
    }

    /**
     * 获取填报数据（单个预算表）
     */
    @Override
    public List<BudgetFillVo> getFillData(Long planId, String templateCode, Long deptId) {
        Long currentDeptId = getCurrentDeptId(deptId);
        return queryFillData(planId, templateCode, currentDeptId);
    }

    /**
     * 获取所有板块填报数据
     */
    @Override
    public List<BudgetFillVo> getAllFillData(Long planId, Long deptId) {
        Long currentDeptId = getCurrentDeptId(deptId);
        // 查询该方案下所有模板科目，不区分templateCode
        return queryFillData(planId, null, currentDeptId);
    }

    /**
     * 查询填报数据核心逻辑
     *
     * 按部门分流：
     *  - 部室（在 dept_budget_module 中配置了填报范围）=> 只返回该部门可填报的科目
     *    及其祖先汇总节点，隐藏其他部门的科目分支
     *  - 集团本部/未配置范围（如 9200 本部、子公司）= 聚合视图，返回该模板全部科目
     *
     * @param planId        预算方案ID
     * @param templateCode  预算表编号（null表示查全部）
     * @param deptId        部门ID
     */
    private List<BudgetFillVo> queryFillData(Long planId, String templateCode, Long deptId) {
        validatePlanOpen(planId);
        // 1. 查询部门在各模板下配置的可填报科目：templateCode -> Set<itemCode>
        Map<String, Set<String>> scopes = loadDeptScopes(planId, deptId, templateCode);

        // 2. 查询模板科目结构（方案级：优先该方案科目快照，未初始化则回退基础模板 plan_id=0）
        List<BudgetTemplateItem> allItems;
        if (planId != null) {
            allItems = budgetTemplateItemMapper.selectList(
                new LambdaQueryWrapper<BudgetTemplateItem>()
                    .eq(BudgetTemplateItem::getPlanId, planId)
                    .eq(templateCode != null, BudgetTemplateItem::getTemplateCode, templateCode)
                    .orderByAsc(BudgetTemplateItem::getTemplateCode)
                    .orderByAsc(BudgetTemplateItem::getItemOrder));
        } else {
            allItems = new ArrayList<>();
        }
        if (CollUtil.isEmpty(allItems)) {
            allItems = budgetTemplateItemMapper.selectList(
                new LambdaQueryWrapper<BudgetTemplateItem>()
                    .eq(BudgetTemplateItem::getPlanId, 0L)
                    .eq(templateCode != null, BudgetTemplateItem::getTemplateCode, templateCode)
                    .orderByAsc(BudgetTemplateItem::getTemplateCode)
                    .orderByAsc(BudgetTemplateItem::getItemOrder));
        }

        // 3. 查询已有填报数据
        LambdaQueryWrapper<BudgetData> dataWrapper = new LambdaQueryWrapper<>();
        dataWrapper.eq(BudgetData::getPlanId, planId)
            .eq(BudgetData::getDeptId, deptId);
        if (templateCode != null) {
            dataWrapper.eq(BudgetData::getTemplateCode, templateCode);
        }
        List<BudgetData> existingDataList = budgetDataMapper.selectList(dataWrapper);
        Map<String, BudgetData> existingDataMap = existingDataList.stream()
            .collect(Collectors.toMap(d -> d.getTemplateCode() + "_" + d.getItemCode(), d -> d, (a, b) -> a));

        // 按模板计算"保留的科目集合"与"可编辑集合"
        Map<String, List<BudgetTemplateItem>> byTemplate = allItems.stream()
            .collect(Collectors.groupingBy(BudgetTemplateItem::getTemplateCode, LinkedHashMap::new, Collectors.toList()));
        Map<String, Set<String>> keepByTemplate = new HashMap<>();
        Map<String, Set<String>> editableByTemplate = new HashMap<>();
        for (Map.Entry<String, List<BudgetTemplateItem>> e : byTemplate.entrySet()) {
            String t = e.getKey();
            Set<String> scope = scopes.get(t);
            List<BudgetTemplateItem> templItems = e.getValue();
            if (scope == null || scope.isEmpty()) {
                // 无范围限制：保留全部，可编辑性沿用模板配置
                keepByTemplate.put(t, templItems.stream()
                    .map(BudgetTemplateItem::getItemCode).collect(Collectors.toSet()));
                editableByTemplate.put(t, templItems.stream()
                    .filter(i -> Long.valueOf(1L).equals(i.getIsEditable()))
                    .map(BudgetTemplateItem::getItemCode).collect(Collectors.toSet()));
            } else {
                // 有范围限制（部室）：保留 允许叶 + 全部祖先汇总节点
                Set<String> keep = new HashSet<>(scope);
                addAncestors(templItems, keep);
                keepByTemplate.put(t, keep);
                editableByTemplate.put(t, scope);
            }
        }

        // 4. 组装返回数据
        List<BudgetFillVo> result = new ArrayList<>();
        for (BudgetTemplateItem item : allItems) {
            // 按"适用公司"过滤：科目设置了 org_scope 且不含当前公司时，对该公司不展示
            if (!BudgetScopeUtil.appliesTo(item.getOrgScope(), deptId)) {
                continue;
            }
            Set<String> keep = keepByTemplate.get(item.getTemplateCode());
            if (keep != null && !keep.contains(item.getItemCode())) {
                continue;
            }
            // 部室（配置了填报范围的部门）只显示其被配置的模板，隐藏未配置的主表板块
            if (!scopes.isEmpty() && !scopes.containsKey(item.getTemplateCode())) {
                continue;
            }
            BudgetFillVo vo = new BudgetFillVo();
            vo.setPlanId(planId);
            vo.setOrgId(deptId);
            vo.setDeptId(deptId);
            vo.setTemplateCode(item.getTemplateCode());
            vo.setTemplateName(item.getTemplateName());
            vo.setTemplateId(item.getTemplateId());
            vo.setRefSubjectId(item.getRefSubjectId());
            vo.setItemCode(item.getItemCode());
            vo.setItemName(item.getItemName());
            vo.setParentCode(item.getParentCode());
            vo.setItemLevel(item.getItemLevel());
            vo.setItemOrder(item.getItemOrder());
            vo.setIsSummary(item.getIsSummary());
            vo.setFormula(item.getFormula());

            // 可编辑性：部门有范围限制时仅允许叶可编辑；汇总/祖先节点只读
            Set<String> editable = editableByTemplate.get(item.getTemplateCode());
            if (editable != null && !editable.isEmpty()) {
                vo.setIsEditable(editable.contains(item.getItemCode()) ? 1L : 0L);
            } else {
                vo.setIsEditable(item.getIsEditable());
            }

            // 填充已有填报数据
            String dataKey = item.getTemplateCode() + "_" + item.getItemCode();
            BudgetData existingData = existingDataMap.get(dataKey);
            if (existingData != null) {
                vo.setId(existingData.getId());
                vo.setBudgetAmount(existingData.getBudgetAmount());
                vo.setLastActual(existingData.getLastActual());
                vo.setExecutionAmount(existingData.getExecutionAmount());
                vo.setRemark(existingData.getRemark());
                vo.setStatus(existingData.getStatus());
            } else {
                vo.setStatus("DRAFT");
            }

            result.add(vo);
        }

        return result;
    }

    /**
     * 加载部门可填报的模板及科目范围。
     * 规则：dept_budget_module 中每有一行 (dept_id, template_code)，该部门即填报该模板。
     *  - item_codes 为空 => 填报该模板全部科目
     *  - item_codes 非空 => 仅填报列出的科目
     * 若某部门没有任何配置行（如集团本部/国资委），视为聚合视图：可查看全部模板。
     */
    private Map<String, Set<String>> loadDeptScopes(Long planId, Long deptId, String templateCode) {
        Map<String, Set<String>> scopes = new LinkedHashMap<>();
        if (deptId == null) {
            return scopes;
        }
        // 板块授权按方案隔离：优先该方案的授权，未初始化则回退基础(plan_id=0)
        LambdaQueryWrapper<DeptBudgetModule> deptWrapper = new LambdaQueryWrapper<>();
        deptWrapper.eq(DeptBudgetModule::getDeptId, deptId);
        if (planId != null) {
            deptWrapper.eq(DeptBudgetModule::getPlanId, planId);
        }
        List<DeptBudgetModule> deptModules = deptBudgetModuleMapper.selectList(deptWrapper);
        if (CollUtil.isEmpty(deptModules) && planId != null) {
            deptModules = deptBudgetModuleMapper.selectList(
                new LambdaQueryWrapper<DeptBudgetModule>()
                    .eq(DeptBudgetModule::getDeptId, deptId)
                    .eq(DeptBudgetModule::getPlanId, 0L));
        }
        for (DeptBudgetModule dm : deptModules) {
            String t = dm.getTemplateCode();
            if (StrUtil.isBlank(t)) {
                continue;
            }
            Set<String> set = scopes.computeIfAbsent(t, k -> new HashSet<>());
            if (StrUtil.isNotBlank(dm.getItemCodes())) {
                set.addAll(Arrays.asList(dm.getItemCodes().split(",")));
            }
        }
        return scopes;
    }

    /**
     * 在保留集合中加入所有祖先汇总节点，确保树形结构完整
     */
    private void addAncestors(List<BudgetTemplateItem> items, Set<String> keep) {
        Map<String, String> parentByCode = new HashMap<>();
        Set<String> codes = new HashSet<>();
        for (BudgetTemplateItem item : items) {
            codes.add(item.getItemCode());
            if (item.getParentCode() != null) {
                parentByCode.put(item.getItemCode(), item.getParentCode());
            }
        }
        for (String leaf : new ArrayList<>(keep)) {
            String p = parentByCode.get(leaf);
            while (p != null && codes.contains(p) && !keep.contains(p)) {
                keep.add(p);
                p = parentByCode.get(p);
            }
        }
    }

    /**
     * 批量保存草稿
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDraft(BudgetFillBo bo) {
        // 保存草稿：不写操作日志，仅落库
        doSave(bo, null, null);
    }

    /**
     * 保存/更新填报数据（供 saveDraft 与 submit 复用）
     * actionType/actionLabel 为 null 时不写操作日志（提交时避免记录中间保存动作）
     */
    @Transactional(rollbackFor = Exception.class)
    public void doSave(BudgetFillBo bo, String actionType, String actionLabel) {
        validatePlanOpen(bo.getPlanId());
        Long deptId = getCurrentDeptId(bo.getDeptId());

        // 查询已有数据
        LambdaQueryWrapper<BudgetData> existingWrapper = new LambdaQueryWrapper<>();
        existingWrapper.eq(BudgetData::getPlanId, bo.getPlanId())
            .eq(BudgetData::getDeptId, deptId);
        if (StrUtil.isNotBlank(bo.getTemplateCode())) {
            existingWrapper.eq(BudgetData::getTemplateCode, bo.getTemplateCode());
        }
        List<BudgetData> existingList = budgetDataMapper.selectList(existingWrapper);
        Map<String, BudgetData> existingMap = existingList.stream()
            .collect(Collectors.toMap(d -> d.getTemplateCode() + "_" + d.getItemCode(), d -> d, (a, b) -> a));

        List<BudgetData> toInsert = new ArrayList<>();

        for (BudgetFillBo.FillItemBo item : bo.getItems()) {
            if (StrUtil.isBlank(item.getItemCode()) || StrUtil.isBlank(item.getTemplateCode())) {
                continue;
            }

            String dataKey = item.getTemplateCode() + "_" + item.getItemCode();
            BudgetData existing = existingMap.get(dataKey);
            if (existing != null) {
                // 只有DRAFT状态（含驳回后回到草稿）的数据才允许修改，SUBMITTED/APPROVED不动
                if ("DRAFT".equals(existing.getStatus())) {
                    // 使用LambdaUpdateWrapper绕过@Version乐观锁
                    LambdaUpdateWrapper<BudgetData> updateWrapper = new LambdaUpdateWrapper<>();
                    updateWrapper.eq(BudgetData::getId, existing.getId())
                        .set(BudgetData::getBudgetAmount, item.getBudgetAmount())
                        .set(BudgetData::getLastActual, item.getLastActual())
                        .set(BudgetData::getStatus, "DRAFT");
                    // B01公司基本信息用remark存文本，其他板块保留原remark（驳回原因不清除）
                    if ("B01".equals(item.getTemplateCode())) {
                        updateWrapper.set(BudgetData::getRemark, item.getRemark());
                    }
                    budgetDataMapper.update(null, updateWrapper);
                }
            } else {
                BudgetData newData = new BudgetData();
                newData.setPlanId(bo.getPlanId());
                newData.setOrgId(deptId);
                newData.setDeptId(deptId);
                newData.setTemplateCode(item.getTemplateCode());
                newData.setItemCode(item.getItemCode());
                newData.setBudgetAmount(item.getBudgetAmount());
                newData.setLastActual(item.getLastActual());
                newData.setRemark(item.getRemark());
                newData.setDataVersion(bo.getDataVersion() != null ? bo.getDataVersion() : "BUDGET");
                newData.setStatus("DRAFT");
                toInsert.add(newData);
            }
        }

        if (CollUtil.isNotEmpty(toInsert)) {
            budgetDataMapper.insertBatch(toInsert);
        }

        // 保存完明细行后，自动计算汇总行(is_summary=1, is_editable=0)的值
        recalcSummaryRows(bo.getPlanId(), deptId, bo.getTemplateCode());

        // 写操作日志
        if (actionType != null) {
            writeOperationLog(bo.getPlanId(), deptId, bo.getTemplateCode(), actionType, actionLabel, "FILL", null);
        }

        // 草稿归集：填报保存后，事务提交时把该方案「填报中」数据归集到目标（本部填报数据 DRAFT）。
        // 归集失败不影响本次填报，仅记日志。
        final Long fillPlanId = bo.getPlanId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    gatherFillQuietly(fillPlanId);
                }
            });
        } else {
            gatherFillQuietly(fillPlanId);
        }
    }

    /**
     * 草稿归集：把该方案各源部门「填报中」金额累加到目标（本部填报数据），异常仅记日志不抛出
     */
    private void gatherFillQuietly(Long planId) {
        try {
            budgetGatherMapService.gatherFill(planId);
        } catch (Exception e) {
            log.error("保存后草稿归集失败, planId={}", planId, e);
        }
    }

    /**
     * 保存明细后自动计算汇总行(is_summary=1, is_editable=0)的budgetAmount和lastActual
     */
    private void recalcSummaryRows(Long planId, Long deptId, String templateCode) {
        // 查模板科目：优先方案级快照，回退基础模板
        LambdaQueryWrapper<BudgetTemplateItem> tplWrapper = new LambdaQueryWrapper<>();
        tplWrapper.eq(BudgetTemplateItem::getPlanId, planId);
        if (StrUtil.isNotBlank(templateCode)) {
            tplWrapper.eq(BudgetTemplateItem::getTemplateCode, templateCode);
        }
        List<BudgetTemplateItem> tplItems = budgetTemplateItemMapper.selectList(tplWrapper);
        if (CollUtil.isEmpty(tplItems)) {
            tplWrapper = new LambdaQueryWrapper<>();
            tplWrapper.eq(BudgetTemplateItem::getPlanId, 0L);
            if (StrUtil.isNotBlank(templateCode)) {
                tplWrapper.eq(BudgetTemplateItem::getTemplateCode, templateCode);
            }
            tplItems = budgetTemplateItemMapper.selectList(tplWrapper);
        }
        if (CollUtil.isEmpty(tplItems)) return;

        // 查当前单位所有填报数据（含汇总行）
        LambdaQueryWrapper<BudgetData> dataWrapper = new LambdaQueryWrapper<>();
        dataWrapper.eq(BudgetData::getPlanId, planId)
            .eq(BudgetData::getDeptId, deptId);
        if (StrUtil.isNotBlank(templateCode)) {
            dataWrapper.eq(BudgetData::getTemplateCode, templateCode);
        }
        List<BudgetData> allData = budgetDataMapper.selectList(dataWrapper);
        if (CollUtil.isEmpty(allData)) return;

        // 按模板+科目编码建索引
        Map<String, BudgetData> dataMap = new HashMap<>();
        for (BudgetData d : allData) {
            dataMap.put(d.getTemplateCode() + "_" + d.getItemCode(), d);
        }

        // 找汇总行并计算
        final List<BudgetTemplateItem> finalTplItems = tplItems;
        for (BudgetTemplateItem tpl : finalTplItems) {
            if (tpl.getIsSummary() == null || tpl.getIsSummary() != 1) continue;
            if (tpl.getIsEditable() != null && tpl.getIsEditable() == 1) continue;

            BigDecimal sumBudget = BigDecimal.ZERO;
            BigDecimal sumActual = BigDecimal.ZERO;
            boolean calculated = false;

            // 优先使用 formula 字段
            String formula = tpl.getFormula();
            if (StrUtil.isNotBlank(formula)) {
                BigDecimal[] result = org.dromara.budget.util.FormulaEngine.evaluate(
                    formula,
                    tpl.getTemplateCode(),
                    tpl.getItemCode(),
                    (tc, ic) -> {
                        BudgetData d = dataMap.get(tc + "_" + ic);
                        if (d == null) return null;
                        return new BigDecimal[]{d.getBudgetAmount(), d.getLastActual()};
                    },
                    (tc, pc) -> getDescendants(tc, pc, finalTplItems)
                );
                if (result != null) {
                    sumBudget = result[0];
                    sumActual = result[1];
                    calculated = true;
                }
            }

            // 无 formula 时回退到原有逻辑：直接子级求和
            if (!calculated) {
                boolean hasChild = false;
                for (BudgetTemplateItem child : finalTplItems) {
                    if (child.getIsSummary() != null && child.getIsSummary() == 1) continue;
                    if (!String.valueOf(tpl.getItemCode()).equals(String.valueOf(child.getParentCode()))) continue;
                    BudgetData cd = dataMap.get(child.getTemplateCode() + "_" + child.getItemCode());
                    if (cd != null) {
                        if (cd.getBudgetAmount() != null) sumBudget = sumBudget.add(cd.getBudgetAmount());
                        if (cd.getLastActual() != null) sumActual = sumActual.add(cd.getLastActual());
                        hasChild = true;
                    }
                }
                if (!hasChild) continue;
            }

            // 更新汇总行的值
            BudgetData sumData = dataMap.get(tpl.getTemplateCode() + "_" + tpl.getItemCode());
            if (sumData != null) {
                LambdaUpdateWrapper<BudgetData> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(BudgetData::getId, sumData.getId())
                    .set(BudgetData::getBudgetAmount, sumBudget)
                    .set(BudgetData::getLastActual, sumActual);
                budgetDataMapper.update(null, updateWrapper);
            } else {
                // 汇总行在budget_data中不存在，创建一条
                BudgetData newData = new BudgetData();
                newData.setPlanId(planId);
                newData.setOrgId(deptId);
                newData.setDeptId(deptId);
                newData.setTemplateCode(tpl.getTemplateCode());
                newData.setItemCode(tpl.getItemCode());
                newData.setBudgetAmount(sumBudget);
                newData.setLastActual(sumActual);
                newData.setDataVersion("BUDGET");
                newData.setStatus("DRAFT");
                budgetDataMapper.insert(newData);
            }
        }
    }

    /**
     * 递归查找所有非汇总子孙科目的itemCode
     */
    private List<String> getDescendants(String tplCode, String parentCode, List<BudgetTemplateItem> tplItems) {
        List<String> result = new ArrayList<>();
        for (BudgetTemplateItem child : tplItems) {
            if (child.getIsSummary() != null && child.getIsSummary() == 1) continue;
            if (tplCode.equals(child.getTemplateCode()) && parentCode.equals(String.valueOf(child.getParentCode()))) {
                result.add(child.getItemCode());
                result.addAll(getDescendants(tplCode, child.getItemCode(), tplItems));
            }
        }
        return result;
    }

    /**
     * 批量提交审批
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(BudgetFillBo bo) {
        validatePlanOpen(bo.getPlanId());
        // 提交前先落草稿数据，但不记录"保存草稿"日志
        doSave(bo, null, null);

        // 提交校验拦截（PRD 7.3）：存在 ERROR 级（逻辑/完整性）错误时拒绝提交
        validateBeforeSubmit(bo);

        // 使用LambdaUpdateWrapper绕过@Version乐观锁，只将DRAFT状态的数据改为SUBMITTED
        Long deptId = getCurrentDeptId(bo.getDeptId());
        LambdaUpdateWrapper<BudgetData> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(BudgetData::getPlanId, bo.getPlanId())
            .eq(BudgetData::getDeptId, deptId)
            .eq(BudgetData::getStatus, "DRAFT")
            .set(BudgetData::getStatus, "SUBMITTED")
            .setSql("remark = CASE WHEN remark LIKE '【驳回】%' THEN NULL ELSE remark END");
        if (StrUtil.isNotBlank(bo.getTemplateCode())) {
            updateWrapper.eq(BudgetData::getTemplateCode, bo.getTemplateCode());
        }
        budgetDataMapper.update(null, updateWrapper);

        // 写"提交审批"日志
        writeOperationLog(bo.getPlanId(), deptId, bo.getTemplateCode(), "SUBMIT", "提交审批", "FILL", bo.getSubmitReason());

        // 创建独立审批记录（每次提交生成新记录，不复用旧数据）
        createApprovalRecord(bo.getPlanId(), deptId, bo.getTemplateCode(), bo.getSubmitReason());

        // 两上两下：提交时对当前数据生成不可变版本快照（V1/V2...），支撑版本对比与多轮修订
        budgetDataVersionService.snapshot(bo.getPlanId(), deptId, bo.getTemplateCode());

        // 多级审批链：三级/四级孙公司填报时，从本级开始自下而上逐级审批（无上级单位不生成链，走原单级审批）
        // 按表分批：每条链固定对应一张预算表(templateCode)，实现"通过的表→正式、未通过→填报"
        budgetApprovalChainService.createChain(bo.getPlanId(), deptId, bo.getTemplateCode(), bo.getSubmitReason());
    }

    /**
     * 提交前调用三类校验，存在 ERROR 级错误即抛异常阻止提交
     */
    private void validateBeforeSubmit(BudgetFillBo bo) {
        Long deptId = getCurrentDeptId(bo.getDeptId());
        BudgetValidateQuery q = new BudgetValidateQuery();
        q.setPlanId(bo.getPlanId());
        q.setOrgId(deptId);
        q.setTemplateCode(bo.getTemplateCode());
        BudgetValidateResult res = budgetValidateService.validate(q);
        if (res == null || res.getErrorCount() == 0) {
            return;
        }
        StringBuilder sb = new StringBuilder("提交失败：存在 " + res.getErrorCount() + " 处校验错误，请修正后再提交。");
        int shown = 0;
        if (CollUtil.isNotEmpty(res.getItems())) {
            for (BudgetValidateResult.ValidateItem it : res.getItems()) {
                if ("ERROR".equals(it.getLevel()) && shown < 5) {
                    sb.append("\n· ").append(StrUtil.blankToDefault(it.getItemName(), it.getRule()))
                        .append(it.getMessage() != null ? "（" + it.getMessage() + "）" : "");
                    shown++;
                }
            }
        }
        throw new ServiceException(sb.toString());
    }

    /**
     * 创建独立审批记录（每次提交生成新记录）
     * 若templateCode为空，则按预算表分别创建多条审批记录
     */
    private void createApprovalRecord(Long planId, Long deptId, String templateCode, String submitReason) {
        // 获取填报单位名称
        String deptName = null;
        SysDept dept = sysDeptMapper.selectById(deptId);
        if (dept != null) {
            deptName = dept.getDeptName();
        }

        // 确定需要创建记录的预算表列表
        Set<String> templateCodes = new LinkedHashSet<>();
        if (StrUtil.isNotBlank(templateCode)) {
            templateCodes.add(templateCode);
        } else {
            // templateCode为空时，从当前提交的数据中找出所有预算表
            LambdaQueryWrapper<BudgetData> tplDataWrapper = new LambdaQueryWrapper<>();
            tplDataWrapper.eq(BudgetData::getPlanId, planId)
                .eq(BudgetData::getDeptId, deptId)
                .eq(BudgetData::getStatus, "SUBMITTED")
                .select(BudgetData::getTemplateCode);
            List<BudgetData> tplDataList = budgetDataMapper.selectList(tplDataWrapper);
            for (BudgetData d : tplDataList) {
                if (StrUtil.isNotBlank(d.getTemplateCode())) {
                    templateCodes.add(d.getTemplateCode());
                }
            }
            // 兼容：SUBMITTED状态可能还没更新（在更新之前调用），再查一次DRAFT
            if (templateCodes.isEmpty()) {
                LambdaQueryWrapper<BudgetData> draftWrapper = new LambdaQueryWrapper<>();
                draftWrapper.eq(BudgetData::getPlanId, planId)
                    .eq(BudgetData::getDeptId, deptId)
                    .eq(BudgetData::getStatus, "DRAFT")
                    .select(BudgetData::getTemplateCode);
                List<BudgetData> draftList = budgetDataMapper.selectList(draftWrapper);
                for (BudgetData d : draftList) {
                    if (StrUtil.isNotBlank(d.getTemplateCode())) {
                        templateCodes.add(d.getTemplateCode());
                    }
                }
            }
        }

        // 预加载所有模板科目，标记汇总行和模板名称
        Map<String, Set<String>> summaryMap = new HashMap<>(); // templateCode -> set of summary itemCodes
        Map<String, String> templateNameMap = new HashMap<>();
        LambdaQueryWrapper<BudgetTemplateItem> tplWrapper = new LambdaQueryWrapper<>();
        tplWrapper.eq(BudgetTemplateItem::getPlanId, 0L);
        if (!templateCodes.isEmpty()) {
            tplWrapper.in(BudgetTemplateItem::getTemplateCode, templateCodes);
        }
        List<BudgetTemplateItem> tplList = budgetTemplateItemMapper.selectList(tplWrapper);
        for (BudgetTemplateItem t : tplList) {
            if (t.getIsSummary() != null && t.getIsSummary() == 1L) {
                summaryMap.computeIfAbsent(t.getTemplateCode(), k -> new HashSet<>()).add(t.getItemCode());
            }
            templateNameMap.put(t.getTemplateCode(), t.getTemplateName());
        }

        Long currentUserId = LoginHelper.getUserId();
        String currentUserName = LoginHelper.getUsername();
        Date now = new Date();

        // 按每个预算表创建一条审批记录
        for (String tplCode : templateCodes) {
            // 计算提交轮次：当前最大轮次+1
            LambdaQueryWrapper<BudgetApprovalRecord> roundWrapper = new LambdaQueryWrapper<>();
            roundWrapper.eq(BudgetApprovalRecord::getPlanId, planId)
                .eq(BudgetApprovalRecord::getDeptId, deptId)
                .eq(BudgetApprovalRecord::getTemplateCode, tplCode)
                .orderByDesc(BudgetApprovalRecord::getSubmitRound)
                .last("LIMIT 1");
            BudgetApprovalRecord latestRecord = budgetApprovalRecordMapper.selectOne(roundWrapper);
            int nextRound = latestRecord != null && latestRecord.getSubmitRound() != null
                ? latestRecord.getSubmitRound() + 1 : 1;

            // 统计该表的可编辑科目数和预算合计（排除汇总行）
            LambdaQueryWrapper<BudgetData> dataWrapper = new LambdaQueryWrapper<>();
            dataWrapper.eq(BudgetData::getPlanId, planId)
                .eq(BudgetData::getDeptId, deptId)
                .eq(BudgetData::getTemplateCode, tplCode);
            List<BudgetData> dataList = budgetDataMapper.selectList(dataWrapper);

            Set<String> summaryItemCodes = summaryMap.getOrDefault(tplCode, Collections.emptySet());
            int itemCount = 0;
            BigDecimal totalAmount = BigDecimal.ZERO;
            for (BudgetData d : dataList) {
                if (!summaryItemCodes.contains(d.getItemCode())) {
                    itemCount++;
                    if (d.getBudgetAmount() != null) {
                        totalAmount = totalAmount.add(d.getBudgetAmount());
                    }
                }
            }

            BudgetApprovalRecord record = new BudgetApprovalRecord();
            record.setPlanId(planId);
            record.setDeptId(deptId);
            record.setDeptName(deptName);
            record.setTemplateCode(tplCode);
            record.setTemplateName(templateNameMap.get(tplCode));
            record.setSubmitRound(nextRound);
            record.setStatus("PENDING");
            record.setSubmitBy(currentUserId);
            record.setSubmitByName(currentUserName);
            record.setSubmitTime(now);
            record.setSubmitReason(submitReason);
            record.setItemCount(itemCount);
            record.setTotalAmount(totalAmount);
            budgetApprovalRecordMapper.insert(record);
        }
    }

    /**
     * 写一条预算操作日志
     */
    private void writeOperationLog(Long planId, Long deptId, String templateCode,
                                   String actionType, String actionLabel, String targetType, String remark) {
        BudgetOperationLog log = new BudgetOperationLog();
        log.setPlanId(planId);
        log.setDeptId(deptId);
        log.setTemplateCode(templateCode);
        log.setPlanName(resolvePlanName(planId));
        log.setTemplateName(resolveTemplateName(templateCode));
        log.setActionType(actionType);
        log.setActionLabel(actionLabel);
        log.setTargetType(targetType);
        log.setOperatorId(LoginHelper.getUserId());
        log.setOperatorName(LoginHelper.getUsername());
        log.setRemark(remark);
        budgetOperationLogMapper.insert(log);
    }

    private String resolvePlanName(Long planId) {
        if (planId == null) return null;
        BudgetPlan plan = budgetPlanMapper.selectById(planId);
        return plan != null ? plan.getPlanName() : null;
    }

    private String resolveTemplateName(String templateCode) {
        if (StrUtil.isBlank(templateCode)) return null;
        List<BudgetTemplateItem> list = budgetTemplateItemMapper.selectList(
            new LambdaQueryWrapper<BudgetTemplateItem>()
                .eq(BudgetTemplateItem::getTemplateCode, templateCode)
                .last("LIMIT 1"));
        return CollUtil.isNotEmpty(list) ? list.get(0).getTemplateName() : null;
    }
}
