package org.dromara.budget.service.impl;

import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.core.page.PageQuery;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.dromara.budget.domain.bo.BudgetGatherMapBo;
import org.dromara.budget.domain.vo.BudgetGatherMapVo;
import org.dromara.budget.domain.BudgetGatherMap;
import org.dromara.budget.domain.BudgetData;
import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.budget.domain.BudgetOperationLog;
import org.dromara.budget.domain.BudgetPlan;
import org.dromara.budget.mapper.BudgetGatherMapMapper;
import org.dromara.budget.mapper.BudgetDataMapper;
import org.dromara.budget.mapper.BudgetTemplateItemMapper;
import org.dromara.budget.mapper.BudgetOperationLogMapper;
import org.dromara.budget.mapper.BudgetPlanMapper;
import org.dromara.budget.service.IBudgetGatherMapService;
import cn.hutool.core.collection.CollUtil;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.domain.SysDept;
import org.dromara.system.mapper.SysDeptMapper;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * 预算科目归集映射Service业务层处理
 *
 * 归集口径：
 * 1. 识别「集团本部」节点（部门名含@（本部）或(本部)），其直属下级即「本部内设部门」。
 * 2. 读取这些部门在指定方案下 17 表(集团本部内设部门费用)填报的预算数据。
 * 3. 按 budget_gather_map 中「部门+源科目 → 目标科目」的映射，将各部门 17 表明细金额累加到本部在 06 表的目标科目。
 * 4. 归集结果以「本部节点 deptId + 06表 + 目标科目」写入/更新 budget_data，dataVersion=BUDGET。
 *
 * @author Lion Li
 * @date 2026-09-16
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetGatherMapServiceImpl implements IBudgetGatherMapService {

    private final BudgetGatherMapMapper baseMapper;
    private final BudgetDataMapper budgetDataMapper;
    private final SysDeptMapper sysDeptMapper;
    private final BudgetTemplateItemMapper budgetTemplateItemMapper;
    private final BudgetOperationLogMapper budgetOperationLogMapper;
    private final BudgetPlanMapper budgetPlanMapper;

    @Override
    public BudgetGatherMapVo queryById(Long id) {
        BudgetGatherMapVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillTemplateNames(Collections.singletonList(vo));
        }
        return vo;
    }

    @Override
    public TableDataInfo<BudgetGatherMapVo> queryPageList(BudgetGatherMapBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BudgetGatherMap> lqw = buildQueryWrapper(bo);
        Page<BudgetGatherMapVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        fillTemplateNames(result.getRecords());
        refreshDeptNames(result.getRecords());
        return TableDataInfo.build(result);
    }

    @Override
    public List<BudgetGatherMapVo> queryList(BudgetGatherMapBo bo) {
        LambdaQueryWrapper<BudgetGatherMap> lqw = buildQueryWrapper(bo);
        List<BudgetGatherMapVo> list = baseMapper.selectVoList(lqw);
        fillTemplateNames(list);
        refreshDeptNames(list);
        return list;
    }

    /**
     * 按 deptId 实时解析部门完整路径(多级用 -> 分隔)，覆盖表中可能过期的快照名。
     * 部门改名或调整层级(如子公司换挂其他公司)后，列表展示自动同步最新架构。
     */
    private void refreshDeptNames(List<BudgetGatherMapVo> rows) {
        if (CollUtil.isEmpty(rows)) {
            return;
        }
        Map<Long, String> pathMap = buildDeptPathMap();
        if (pathMap.isEmpty()) {
            return;
        }
        for (BudgetGatherMapVo v : rows) {
            if (v.getDeptId() != null) {
                String p = pathMap.get(v.getDeptId());
                if (p != null) {
                    v.setDeptName(p);
                }
            }
            if (v.getTgtDeptId() != null) {
                String p = pathMap.get(v.getTgtDeptId());
                if (p != null) {
                    v.setTgtDeptName(p);
                }
            }
        }
    }

    /**
     * 构建 deptId → 完整路径 映射(源自当前系统部门架构)
     */
    private Map<Long, String> buildDeptPathMap() {
        List<SysDept> all = sysDeptMapper.selectList(null);
        if (CollUtil.isEmpty(all)) {
            return new HashMap<>();
        }
        Map<Long, SysDept> byId = all.stream()
            .collect(Collectors.toMap(SysDept::getDeptId, d -> d, (a, b) -> a));
        Map<Long, String> pathMap = new HashMap<>();
        for (SysDept d : all) {
            pathMap.put(d.getDeptId(), computeDisplayPath(d, byId));
        }
        return pathMap;
    }

    /**
     * 由 deptId 向上回溯拼真实完整路径(不依赖可能损坏的 ancestors)，
     * 再去掉最前两层(国资委、集团壳)，从一级子公司/本部层开始展示。
     */
    private String computeDisplayPath(SysDept d, Map<Long, SysDept> byId) {
        List<String> names = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        SysDept cur = d;
        while (cur != null && cur.getDeptName() != null) {
            names.add(cur.getDeptName());
            Long pid = cur.getParentId();
            if (pid == null || pid == 0L) {
                break;
            }
            if (!seen.add(pid)) {
                break;
            }
            cur = byId.get(pid);
        }
        Collections.reverse(names);
        return names.size() > 2
            ? String.join("->", names.subList(2, names.size()))
            : String.join("->", names);
    }

    /**
     * 回填源/目标预算表全名，供列表展示
     */
    private void fillTemplateNames(List<BudgetGatherMapVo> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        Set<String> codes = new HashSet<>();
        for (BudgetGatherMapVo v : list) {
            if (StringUtils.isNotBlank(v.getSrcTemplateCode())) {
                codes.add(v.getSrcTemplateCode());
            }
            if (StringUtils.isNotBlank(v.getTgtTemplateCode())) {
                codes.add(v.getTgtTemplateCode());
            }
        }
        if (codes.isEmpty()) {
            return;
        }
        Map<String, String> nameMap = new HashMap<>();
        List<BudgetTemplateItem> tpls = budgetTemplateItemMapper.selectList(
            Wrappers.<BudgetTemplateItem>lambdaQuery()
                .select(BudgetTemplateItem::getTemplateCode, BudgetTemplateItem::getTemplateName)
                .in(BudgetTemplateItem::getTemplateCode, codes)
                .eq(BudgetTemplateItem::getPlanId, 0));
        for (BudgetTemplateItem t : tpls) {
            if (StringUtils.isNotBlank(t.getTemplateCode()) && StringUtils.isNotBlank(t.getTemplateName())) {
                nameMap.put(t.getTemplateCode(), t.getTemplateName());
            }
        }
        for (BudgetGatherMapVo v : list) {
            v.setSrcTemplateName(nameMap.get(v.getSrcTemplateCode()));
            v.setTgtTemplateName(nameMap.get(v.getTgtTemplateCode()));
        }
    }

    private LambdaQueryWrapper<BudgetGatherMap> buildQueryWrapper(BudgetGatherMapBo bo) {
        LambdaQueryWrapper<BudgetGatherMap> lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(BudgetGatherMap::getId);
        lqw.eq(bo.getPlanId() != null, BudgetGatherMap::getPlanId, bo.getPlanId());
        lqw.eq(bo.getDeptId() != null, BudgetGatherMap::getDeptId, bo.getDeptId());
        lqw.eq(bo.getTgtDeptId() != null, BudgetGatherMap::getTgtDeptId, bo.getTgtDeptId());
        lqw.eq(StringUtils.isNotBlank(bo.getSrcTemplateCode()), BudgetGatherMap::getSrcTemplateCode, bo.getSrcTemplateCode());
        lqw.eq(StringUtils.isNotBlank(bo.getSrcItemCode()), BudgetGatherMap::getSrcItemCode, bo.getSrcItemCode());
        lqw.eq(StringUtils.isNotBlank(bo.getTgtItemCode()), BudgetGatherMap::getTgtItemCode, bo.getTgtItemCode());
        return lqw;
    }

    @Override
    public Boolean insertByBo(BudgetGatherMapBo bo) {
        BudgetGatherMap add = MapstructUtils.convert(bo, BudgetGatherMap.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    @Override
    public Boolean updateByBo(BudgetGatherMapBo bo) {
        BudgetGatherMap update = MapstructUtils.convert(bo, BudgetGatherMap.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    private void validEntityBeforeSave(BudgetGatherMap entity) {
        // 同一「方案+部门+源表+源科目+目标科目」不允许重复，避免重复累加
        LambdaQueryWrapper<BudgetGatherMap> dup = Wrappers.lambdaQuery();
        dup.eq(BudgetGatherMap::getPlanId, entity.getPlanId());
        dup.eq(BudgetGatherMap::getDeptId, entity.getDeptId());
        dup.eq(BudgetGatherMap::getSrcTemplateCode, entity.getSrcTemplateCode());
        dup.eq(BudgetGatherMap::getSrcItemCode, entity.getSrcItemCode());
        dup.eq(BudgetGatherMap::getTgtTemplateCode, entity.getTgtTemplateCode());
        dup.eq(BudgetGatherMap::getTgtItemCode, entity.getTgtItemCode());
        if (entity.getId() != null) {
            dup.ne(BudgetGatherMap::getId, entity.getId());
        }
        if (baseMapper.selectCount(dup) > 0) {
            throw new org.dromara.common.core.exception.ServiceException("该归集映射已存在，请勿重复添加");
        }
    }

    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        return baseMapper.deleteByIds(ids) > 0;
    }

    /**
     * 执行归集：按映射把「源部门源科目」金额累加到「目标部门目标科目」
     * 目标部门：取映射 tgtDeptId，为空则默认为本部节点（兼容「部门17表→本部06表」）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer gather(Long planId) {
        // 正式归集：取「审批完全通过」的源数据，写目标为 APPROVED（正式预算）
        return doGather(planId, List.of("APPROVED"), "APPROVED");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer gatherFill(Long planId) {
        // 草稿归集：取「填报中」（草稿/提交）的源数据，写目标为 DRAFT（本部填报数据）
        return doGather(planId, List.of("DRAFT", "SUBMITTED"), "DRAFT");
    }

    /**
     * 归集核心：按映射把源部门预算数据累加到目标部门目标科目（双行并存：草稿DRAFT / 正式APPROVED）
     *
     * @param planId      方案ID
     * @param srcStatuses 源数据状态集合（决定取数口径）
     * @param tgtStatus   目标行状态（决定区分填报数据/正式预算）
     */
    private Integer doGather(Long planId, List<String> srcStatuses, String tgtStatus) {
        // 1. 识别本部节点（目标部门为空时的默认汇聚目标）
        List<SysDept> allDepts = sysDeptMapper.selectList(null);
        SysDept hq = allDepts.stream()
            .filter(d -> isHqName(d.getDeptName()))
            .findFirst().orElse(null);
        if (hq == null) {
            throw new org.dromara.common.core.exception.ServiceException("未找到集团本部节点，无法执行归集");
        }
        Long hqId = hq.getDeptId();

        // 2. 读取归集映射
        List<BudgetGatherMap> maps = baseMapper.selectList(
            Wrappers.<BudgetGatherMap>lambdaQuery()
                .eq(planId != null, BudgetGatherMap::getPlanId, planId));
        if (CollUtil.isEmpty(maps)) {
            return 0;
        }
        // 源部门集合 = 映射中出现过的所有源部门ID
        Set<Long> srcDeptIds = maps.stream()
            .map(BudgetGatherMap::getDeptId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (srcDeptIds.isEmpty()) {
            return 0;
        }

        // 3. 读取这些源部门在指定方案下、指定状态口径的预算数据（dataVersion=BUDGET）
        LambdaQueryWrapper<BudgetData> srcWrapper = Wrappers.lambdaQuery();
        srcWrapper.eq(BudgetData::getPlanId, planId);
        srcWrapper.in(BudgetData::getDeptId, srcDeptIds);
        srcWrapper.eq(BudgetData::getDataVersion, "BUDGET");
        srcWrapper.in(BudgetData::getStatus, srcStatuses);
        List<BudgetData> srcDataList = budgetDataMapper.selectList(srcWrapper);

        // 4. 累加：key = deptId + srcTemplateCode + srcItemCode
        Map<String, BigDecimal> srcAmountMap = new HashMap<>();
        for (BudgetData d : srcDataList) {
            String key = d.getDeptId() + "_" + d.getTemplateCode() + "_" + d.getItemCode();
            BigDecimal val = d.getBudgetAmount();
            srcAmountMap.merge(key, val == null ? BigDecimal.ZERO : val, BigDecimal::add);
        }

        // 5. 按映射累计到 目标部门目标科目 (tgtDeptId + tgtTemplateCode + tgtItemCode)
        Map<String, BigDecimal> tgtAccumMap = new HashMap<>();
        Set<String> tgtKeys = new HashSet<>();
        for (BudgetGatherMap m : maps) {
            if (m.getDeptId() == null
                || StringUtils.isBlank(m.getSrcTemplateCode()) || StringUtils.isBlank(m.getSrcItemCode())
                || StringUtils.isBlank(m.getTgtTemplateCode()) || StringUtils.isBlank(m.getTgtItemCode())) {
                continue;
            }
            String srcKey = m.getDeptId() + "_" + m.getSrcTemplateCode() + "_" + m.getSrcItemCode();
            BigDecimal srcVal = srcAmountMap.getOrDefault(srcKey, BigDecimal.ZERO);
            long tgtDeptId = m.getTgtDeptId() == null ? hqId : m.getTgtDeptId();
            String tgtKey = tgtDeptId + "|" + m.getTgtTemplateCode() + "|" + m.getTgtItemCode();
            tgtKeys.add(tgtKey);
            tgtAccumMap.merge(tgtKey, srcVal, BigDecimal::add);
        }
        if (tgtKeys.isEmpty()) {
            return 0;
        }

        // 6. 写入/更新目标部门在目标表（dataVersion=BUDGET 且 status=tgtStatus）的预算数据
        Set<Long> tgtDeptIds = new HashSet<>();
        for (String k : tgtKeys) {
            tgtDeptIds.add(Long.parseLong(k.substring(0, k.indexOf('|'))));
        }
        LambdaQueryWrapper<BudgetData> existWrapper = Wrappers.lambdaQuery();
        existWrapper.eq(BudgetData::getPlanId, planId)
            .in(BudgetData::getDeptId, tgtDeptIds)
            .eq(BudgetData::getDataVersion, "BUDGET")
            .eq(BudgetData::getStatus, tgtStatus);
        List<BudgetData> existing = budgetDataMapper.selectList(existWrapper);
        Map<String, BudgetData> existingMap = existing.stream()
            .collect(Collectors.toMap(d -> d.getDeptId() + "|" + d.getTemplateCode() + "|" + d.getItemCode(), d -> d, (a, b) -> a));

        int count = 0;
        for (Map.Entry<String, BigDecimal> e : tgtAccumMap.entrySet()) {
            String tgtKey = e.getKey();
            BigDecimal amount = e.getValue();
            int idx1 = tgtKey.indexOf('|');
            int idx2 = tgtKey.indexOf('|', idx1 + 1);
            long tgtDeptId = Long.parseLong(tgtKey.substring(0, idx1));
            String tplCode = tgtKey.substring(idx1 + 1, idx2);
            String itemCode = tgtKey.substring(idx2 + 1);
            BudgetData existingRow = existingMap.get(tgtKey);
            if (existingRow != null) {
                existingRow.setBudgetAmount(amount);
                budgetDataMapper.updateById(existingRow);
            } else {
                BudgetData row = new BudgetData();
                row.setPlanId(planId);
                row.setDeptId(tgtDeptId);
                row.setOrgId(tgtDeptId);
                row.setTemplateCode(tplCode);
                row.setItemCode(itemCode);
                row.setBudgetAmount(amount);
                row.setDataVersion("BUDGET");
                row.setStatus(tgtStatus);
                budgetDataMapper.insert(row);
            }
            count++;
        }
        String statusDesc = "APPROVED".equals(tgtStatus) ? "正式" : "填报";
        writeOperationLog(planId, "执行归集", "GATHER",
            "按归集映射执行归集（" + statusDesc + "），共更新 " + count + " 个目标科目");
        return count;
    }

    /**
     * 写一条预算操作日志（归集留痕）
     */
    private void writeOperationLog(Long planId, String actionLabel, String targetType, String remark) {
        BudgetOperationLog log = new BudgetOperationLog();
        log.setPlanId(planId);
        log.setPlanName(resolvePlanName(planId));
        log.setActionType("GATHER");
        log.setActionLabel(actionLabel);
        log.setTargetType(targetType);
        log.setOperatorId(LoginHelper.getUserId());
        log.setOperatorName(LoginHelper.getUsername());
        log.setRemark(remark);
        budgetOperationLogMapper.insert(log);
    }

    private String resolvePlanName(Long planId) {
        if (planId == null) {
            return null;
        }
        BudgetPlan plan = budgetPlanMapper.selectById(planId);
        return plan == null ? null : plan.getPlanName();
    }

    private boolean isHqName(String name) {
        return name != null && (name.contains("（本部）") || name.contains("(本部)"));
    }

    @Override
    public List<Map<String, Object>> listInnerDepts() {
        List<SysDept> allDepts = sysDeptMapper.selectList(null);
        SysDept hq = allDepts.stream()
            .filter(d -> isHqName(d.getDeptName()))
            .findFirst().orElse(null);
        if (hq == null) {
            return new ArrayList<>();
        }
        return allDepts.stream()
            .filter(d -> hq.getDeptId().equals(d.getParentId()))
            .sorted(Comparator.comparing(SysDept::getOrderNum, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(d -> {
                Map<String, Object> m = new HashMap<>();
                m.put("deptId", d.getDeptId());
                m.put("deptName", d.getDeptName());
                return m;
            })
            .collect(Collectors.toList());
    }
}