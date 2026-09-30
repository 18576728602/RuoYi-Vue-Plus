package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.budget.domain.BudgetData;
import org.dromara.budget.domain.BudgetSubjectMaster;
import org.dromara.budget.domain.BudgetTemplate;
import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.budget.mapper.BudgetDataMapper;
import org.dromara.budget.mapper.BudgetSubjectMasterMapper;
import org.dromara.budget.mapper.BudgetTemplateItemMapper;
import org.dromara.budget.mapper.BudgetTemplateMapper;
import org.dromara.budget.util.BudgetTemplateTypeUtil;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 预算表模板管理(01~17 之外可自扩展)。
 * <p>预算表支持「同编码 + 不同年度」版本共存(budget_template 主键为自增 id,
 * 业务唯一性由 template_code + budget_year 保证)。新增后自动出现在科目所属表下拉、方案选表、填报/矩阵/审批中。</p>
 * <p>预算表自身的科目清单通过「挂载」维护在 budget_template_item(template_id+budget_year)，
 * 挂载行 ref_subject_id 引用科目主数据，科目明细主数据本身不被预算表改动。</p>
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/template")
public class BudgetTemplateController extends BaseController {

    private final BudgetTemplateMapper budgetTemplateMapper;
    private final BudgetSubjectMasterMapper budgetSubjectMasterMapper;
    private final BudgetTemplateItemMapper budgetTemplateItemMapper;
    private final BudgetDataMapper budgetDataMapper;

    @SaCheckPermission(value = {"budget:template:list"}, mode = SaMode.OR)
    @GetMapping("/list")
    public R<List<BudgetTemplate>> list(@RequestParam(value = "budgetYear", required = false) Integer budgetYear) {
        LambdaQueryWrapper<BudgetTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BudgetTemplate::getDelFlag, "0")
            .eq(budgetYear != null, BudgetTemplate::getBudgetYear, budgetYear)
            .orderByAsc(BudgetTemplate::getBudgetYear)
            .orderByAsc(BudgetTemplate::getSortOrder);
        List<BudgetTemplate> list = budgetTemplateMapper.selectList(wrapper);
        // 补充每个预算表下的科目数(挂载清单口径: budget_template_item 按 template_id+年度)
        for (BudgetTemplate t : list) {
            Long cnt = budgetTemplateItemMapper.selectCount(
                new LambdaQueryWrapper<BudgetTemplateItem>()
                    .eq(BudgetTemplateItem::getTemplateId, t.getId())
                    .eq(BudgetTemplateItem::getBudgetYear, t.getBudgetYear()));
            t.setSubjectCount(cnt == null ? 0L : cnt);
        }
        return R.ok(list);
    }

    /**
     * 预算表编码生成：B + 两位序号(全局最大序号+1，含已删除占位，避免复用)。前端新增时自动回填、只读。
     */
    @SaCheckPermission(value = {"budget:template:list"}, mode = SaMode.OR)
    @GetMapping("/nextCode")
    public R<String> nextCode() {
        return R.ok(nextCodeValue());
    }

    private String nextCodeValue() {
        List<BudgetTemplate> all = budgetTemplateMapper.selectList(new LambdaQueryWrapper<>());
        int max = 0;
        if (all != null) {
            for (BudgetTemplate t : all) {
                String code = t.getTemplateCode();
                if (StrUtil.isBlank(code)) {
                    continue;
                }
                String digits = code.replaceAll("[^0-9]", "");
                if (digits.isEmpty()) {
                    continue;
                }
                try {
                    max = Math.max(max, Integer.parseInt(digits));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return String.format(BudgetTemplateTypeUtil.CODE_PREFIX + "%02d", max + 1);
    }

    @SaCheckPermission(value = {"budget:template:add"}, mode = SaMode.OR)
    @Log(title = "预算表模板", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody BudgetTemplate bo) {
        Integer year = bo.getBudgetYear() == null ? 2027 : bo.getBudgetYear();
        if (StrUtil.isBlank(bo.getTemplateCode())) {
            bo.setTemplateCode(nextCodeValue());
        }
        if (StrUtil.isBlank(bo.getTemplateName())) {
            throw new ServiceException("预算表名称不能为空");
        }
        // 判重: 同编码 + 同年份 唯一
        Long dup = budgetTemplateMapper.selectCount(new LambdaQueryWrapper<BudgetTemplate>()
            .eq(BudgetTemplate::getTemplateCode, bo.getTemplateCode())
            .eq(BudgetTemplate::getBudgetYear, year)
            .eq(BudgetTemplate::getDelFlag, "0"));
        if (dup != null && dup > 0) {
            throw new ServiceException("预算表编码[" + bo.getTemplateCode() + "]在" + year + "年度已存在");
        }
        bo.setBudgetYear(year);
        bo.setStatus(StrUtil.blankToDefault(bo.getStatus(), "0"));
        bo.setTemplateType(StrUtil.blankToDefault(bo.getTemplateType(), "BASE"));
        bo.setSortOrder(bo.getSortOrder() == null ? 99 : bo.getSortOrder());
        bo.setDelFlag("0");
        bo.setVersion(0);
        bo.setCreateBy(LoginHelper.getUserId());
        bo.setCreateDept(LoginHelper.getDeptId());
        bo.setCreateTime(new Date());
        int n = budgetTemplateMapper.insert(bo);
        // 仅创建预算表本身，不自动改写科目主数据；科目明细由后续「挂载」维护在该表的挂接清单中
        return toAjax(n);
    }

    @SaCheckPermission(value = {"budget:template:edit"}, mode = SaMode.OR)
    @Log(title = "预算表模板", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody BudgetTemplate bo) {
        if (bo.getId() == null) {
            throw new ServiceException("预算表ID不能为空");
        }
        if (StrUtil.isBlank(bo.getTemplateCode())) {
            throw new ServiceException("预算表编码不能为空");
        }
        if (StrUtil.isBlank(bo.getTemplateName())) {
            throw new ServiceException("预算表名称不能为空");
        }
        BudgetTemplate exist = budgetTemplateMapper.selectById(bo.getId());
        if (exist == null) {
            throw new ServiceException("预算表不存在");
        }
        Integer year = bo.getBudgetYear() == null ? exist.getBudgetYear() : bo.getBudgetYear();
        // 编辑时编码/年份变更需校验唯一(排除自身)
        Long dup = budgetTemplateMapper.selectCount(new LambdaQueryWrapper<BudgetTemplate>()
            .eq(BudgetTemplate::getTemplateCode, StrUtil.blankToDefault(bo.getTemplateCode(), exist.getTemplateCode()))
            .eq(BudgetTemplate::getBudgetYear, year)
            .eq(BudgetTemplate::getDelFlag, "0")
            .ne(BudgetTemplate::getId, bo.getId()));
        if (dup != null && dup > 0) {
            throw new ServiceException("预算表编码在" + year + "年度已存在");
        }
        bo.setBudgetYear(year);
        bo.setUpdateBy(LoginHelper.getUserId());
        bo.setUpdateTime(new Date());
        return toAjax(budgetTemplateMapper.updateById(bo));
    }

    @SaCheckPermission(value = {"budget:template:remove"}, mode = SaMode.OR)
    @Log(title = "预算表模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        BudgetTemplate exist = budgetTemplateMapper.selectById(id);
        if (exist == null) {
            throw new ServiceException("预算表不存在");
        }
        Long cSubject = budgetTemplateItemMapper.selectCount(
            new LambdaQueryWrapper<BudgetTemplateItem>()
                .eq(BudgetTemplateItem::getTemplateId, id));
        if (cSubject != null && cSubject > 0) {
            throw new ServiceException("该预算表下已挂载科目，禁止删除(请先解挂)");
        }
        Long cData = budgetDataMapper.selectCount(
            new LambdaQueryWrapper<BudgetData>().eq(BudgetData::getTemplateCode, exist.getTemplateCode()));
        if (cData != null && cData > 0) {
            throw new ServiceException("该预算表已有填报数据，禁止删除");
        }
        BudgetTemplate bo = new BudgetTemplate();
        bo.setId(id);
        bo.setDelFlag("1");
        bo.setUpdateBy(LoginHelper.getUserId());
        bo.setUpdateTime(new Date());
        return toAjax(budgetTemplateMapper.updateById(bo));
    }

    /**
     * 复制预算表：复制源表及其「挂载清单」(budget_template_item) 到目标年份。
     * 挂载行是引用主数据的快照(含父级/层级/汇总口径/ref_subject_id)，复制后指向同一主数据，
     * 但归入新模板(template_id=新表id, budget_year=目标年度)。科目主数据本身不被改动。
     */
    @SaCheckPermission(value = {"budget:template:add"}, mode = SaMode.OR)
    @Log(title = "预算表模板", businessType = BusinessType.INSERT)
    @PostMapping("/copy")
    @Transactional(rollbackFor = Exception.class)
    public R<BudgetTemplate> copy(@RequestBody BudgetTemplate req) {
        if (req.getId() == null && StrUtil.isBlank(req.getTemplateCode())) {
            throw new ServiceException("请指定要复制的源预算表");
        }
        BudgetTemplate src;
        if (req.getId() != null) {
            src = budgetTemplateMapper.selectById(req.getId());
        } else {
            src = budgetTemplateMapper.selectOne(new LambdaQueryWrapper<BudgetTemplate>()
                .eq(BudgetTemplate::getTemplateCode, req.getTemplateCode())
                .eq(BudgetTemplate::getBudgetYear, req.getBudgetYear() != null ? req.getBudgetYear() : 2027)
                .eq(BudgetTemplate::getDelFlag, "0").last("LIMIT 1"));
        }
        if (src == null) {
            throw new ServiceException("源预算表不存在");
        }
        int targetYear = req.getTargetYear() != null ? req.getTargetYear() : (src.getBudgetYear() == null ? 2027 : src.getBudgetYear() + 1);
        // 目标年份下是否已存在同编码表
        Long dup = budgetTemplateMapper.selectCount(new LambdaQueryWrapper<BudgetTemplate>()
            .eq(BudgetTemplate::getTemplateCode, src.getTemplateCode())
            .eq(BudgetTemplate::getBudgetYear, targetYear)
            .eq(BudgetTemplate::getDelFlag, "0"));
        if (dup != null && dup > 0) {
            throw new ServiceException("预算表编码[" + src.getTemplateCode() + "]在" + targetYear + "年度已存在");
        }
        // 1) 复制预算表
        BudgetTemplate nw = new BudgetTemplate();
        nw.setTemplateCode(src.getTemplateCode());
        nw.setBudgetYear(targetYear);
        nw.setTemplateName(StrUtil.isBlank(req.getTemplateName()) ? src.getTemplateName() : req.getTemplateName());
        nw.setTemplateType(StrUtil.blankToDefault(src.getTemplateType(), "BASE"));
        nw.setRemark(StrUtil.isBlank(req.getRemark()) ? src.getRemark() : req.getRemark());
        nw.setSortOrder(src.getSortOrder());
        nw.setStatus(src.getStatus());
        nw.setDelFlag("0");
        nw.setVersion(0);
        nw.setCreateBy(LoginHelper.getUserId());
        nw.setCreateDept(LoginHelper.getDeptId());
        nw.setCreateTime(new Date());
        budgetTemplateMapper.insert(nw);
        // 2) 复制源表的挂载清单到新模板(仅复制挂载行，不动主数据)
        int srcYear = src.getBudgetYear() == null ? 2027 : src.getBudgetYear();
        List<BudgetTemplateItem> items = budgetTemplateItemMapper.selectList(
            new LambdaQueryWrapper<BudgetTemplateItem>()
                .eq(BudgetTemplateItem::getTemplateId, src.getId())
                .eq(BudgetTemplateItem::getBudgetYear, srcYear));
        if (CollUtil.isNotEmpty(items)) {
            List<BudgetTemplateItem> copies = new ArrayList<>();
            for (BudgetTemplateItem it : items) {
                BudgetTemplateItem cp = new BudgetTemplateItem();
                cp.setPlanId(0L);
                cp.setTemplateId(nw.getId());
                cp.setBudgetYear(targetYear);
                cp.setTemplateCode(nw.getTemplateCode());
                cp.setTemplateName(nw.getTemplateName());
                cp.setItemCode(it.getItemCode());
                cp.setItemName(it.getItemName());
                cp.setParentCode(it.getParentCode());
                cp.setItemLevel(it.getItemLevel());
                cp.setItemOrder(it.getItemOrder());
                cp.setResponsibleDept(it.getResponsibleDept());
                cp.setIsSummary(it.getIsSummary());
                cp.setIsEditable(it.getIsEditable());
                cp.setFormula(it.getFormula());
                cp.setOrgScope(it.getOrgScope());
                cp.setRefSubjectId(it.getRefSubjectId());
                cp.setVersion(0L);
                cp.setCreateBy(LoginHelper.getUserId());
                cp.setCreateDept(LoginHelper.getDeptId());
                cp.setCreateTime(new Date());
                copies.add(cp);
            }
            budgetTemplateItemMapper.insertBatch(copies);
        }
        return R.ok(nw);
    }

    /**
     * 查询某预算表已挂载的科目清单(扁平，按层级排序)，前端据此组装该表自身的科目树。
     */
    @SaCheckPermission(value = {"budget:template:list"}, mode = SaMode.OR)
    @GetMapping("/items")
    public R<List<BudgetTemplateItem>> items(@RequestParam Long templateId, @RequestParam(required = false) Integer budgetYear) {
        BudgetTemplate tpl = budgetTemplateMapper.selectById(templateId);
        if (tpl == null) {
            return R.ok(new ArrayList<>());
        }
        int year = budgetYear != null ? budgetYear : (tpl.getBudgetYear() == null ? 2027 : tpl.getBudgetYear());
        List<BudgetTemplateItem> items = budgetTemplateItemMapper.selectList(
            new LambdaQueryWrapper<BudgetTemplateItem>()
                .eq(BudgetTemplateItem::getTemplateId, templateId)
                .eq(BudgetTemplateItem::getBudgetYear, year)
                .orderByAsc(BudgetTemplateItem::getItemLevel)
                .orderByAsc(BudgetTemplateItem::getItemOrder)
                .orderByAsc(BudgetTemplateItem::getItemCode));
        return R.ok(items == null ? new ArrayList<>() : items);
    }

    /**
     * 从科目主数据「挂载」一组科目到某预算表：自动补全祖先链，复制主数据快照到该表挂载清单，
     * 已挂载的跳过。仅操作 budget_template_item，不改变科目主数据。
     */
    @SaCheckPermission(value = {"budget:template:add"}, mode = SaMode.OR)
    @Log(title = "预算表模板-挂载科目", businessType = BusinessType.INSERT)
    @PostMapping("/mount")
    @Transactional(rollbackFor = Exception.class)
    public R<Integer> mount(@RequestBody Map<String, Object> body) {
        Long templateId = body.get("templateId") == null ? null : Long.valueOf(String.valueOf(body.get("templateId")));
        if (templateId == null) {
            throw new ServiceException("预算表ID不能为空");
        }
        BudgetTemplate tpl = budgetTemplateMapper.selectById(templateId);
        if (tpl == null) {
            throw new ServiceException("预算表不存在");
        }
        Object idsObj = body.get("subjectIds");
        List<Long> subjectIds = new ArrayList<>();
        if (idsObj instanceof Collection<?> c) {
            for (Object o : c) {
                subjectIds.add(Long.valueOf(String.valueOf(o)));
            }
        }
        if (subjectIds.isEmpty()) {
            throw new ServiceException("请至少选择一个要挂载的科目");
        }
        List<BudgetSubjectMaster> masters = budgetSubjectMasterMapper.selectList(
            new LambdaQueryWrapper<BudgetSubjectMaster>()
                .in(BudgetSubjectMaster::getId, subjectIds)
                .eq(BudgetSubjectMaster::getValidFlag, "1"));
        if (CollUtil.isEmpty(masters)) {
            return R.ok(0);
        }
        // 祖先闭包：父级一并挂载，保证该表科目树完整
        List<BudgetSubjectMaster> all = budgetSubjectMasterMapper.selectList(
            new LambdaQueryWrapper<BudgetSubjectMaster>().eq(BudgetSubjectMaster::getValidFlag, "1"));
        Map<String, BudgetSubjectMaster> byCode = new HashMap<>();
        for (BudgetSubjectMaster m : all) {
            byCode.putIfAbsent(m.getSubjectCode(), m);
        }
        Set<Long> targetIds = new LinkedHashSet<>();
        for (BudgetSubjectMaster m : masters) {
            BudgetSubjectMaster cur = m;
            while (cur != null && targetIds.add(cur.getId())) {
                cur = StrUtil.isNotBlank(cur.getParentCode()) ? byCode.get(cur.getParentCode()) : null;
            }
        }
        List<BudgetSubjectMaster> toCopy = all.stream()
            .filter(m -> targetIds.contains(m.getId()))
            // 一级节点仅为科目明细的分组/表头(如 01公司基本信息表), 不写入预算表挂载清单
            .filter(m -> !(m.getLevel() != null && m.getLevel() == 1))
            .sorted(Comparator.comparingInt(m -> m.getLevel() == null ? 1 : m.getLevel()))
            .toList();
        if (toCopy.isEmpty()) {
            return R.ok(0);
        }
        // 已挂载去重(同一模板+年度+科目编码)
        Set<String> existKeys = new java.util.HashSet<>();
        List<BudgetTemplateItem> exist = budgetTemplateItemMapper.selectList(
            new LambdaQueryWrapper<BudgetTemplateItem>()
                .eq(BudgetTemplateItem::getTemplateId, templateId)
                .eq(BudgetTemplateItem::getBudgetYear, tpl.getBudgetYear()));
        for (BudgetTemplateItem e : exist) {
            existKeys.add(e.getItemCode());
        }
        List<BudgetTemplateItem> inserts = new ArrayList<>();
        int year = tpl.getBudgetYear() == null ? 2027 : tpl.getBudgetYear();
        for (BudgetSubjectMaster m : toCopy) {
            if (existKeys.contains(m.getSubjectCode())) {
                continue;
            }
            existKeys.add(m.getSubjectCode());
            BudgetTemplateItem it = new BudgetTemplateItem();
            it.setPlanId(0L);
            it.setTemplateId(templateId);
            it.setBudgetYear(year);
            it.setTemplateCode(tpl.getTemplateCode());
            it.setTemplateName(tpl.getTemplateName());
            it.setItemCode(m.getSubjectCode());
            it.setItemName(m.getSubjectName());
            it.setParentCode(m.getParentCode());
            it.setItemLevel(m.getLevel() == null ? 1L : Long.valueOf(m.getLevel()));
            it.setItemOrder(m.getSort() == null ? 1L : Long.valueOf(m.getSort()));
            it.setIsEditable((m.getIsSummary() != null && m.getIsSummary() == 1) ? 0L : 1L);
            it.setIsSummary(m.getIsSummary() == null ? 0L : Long.valueOf(m.getIsSummary()));
            it.setOrgScope(m.getOrgScope());
            it.setRefSubjectId(m.getId());
            it.setVersion(0L);
            it.setCreateBy(LoginHelper.getUserId());
            it.setCreateDept(LoginHelper.getDeptId());
            it.setCreateTime(new Date());
            inserts.add(it);
        }
        if (CollUtil.isNotEmpty(inserts)) {
            budgetTemplateItemMapper.insertBatch(inserts);
        }
        return R.ok(inserts.size());
    }

    /**
     * 解挂：从某预算表移除若干已挂载科目（按该表挂载行 id）。
     * 仅逻辑删除该表挂载清单中的行(del_flag 0->1)，不改变科目主数据；父级若因此为空仍保留在挂载清单中。
     */
    @SaCheckPermission(value = {"budget:template:add"}, mode = SaMode.OR)
    @Log(title = "预算表模板-解挂科目", businessType = BusinessType.DELETE)
    @PostMapping("/unmount")
    @Transactional(rollbackFor = Exception.class)
    public R<Integer> unmount(@RequestBody Map<String, Object> body) {
        Long templateId = body.get("templateId") == null ? null : Long.valueOf(String.valueOf(body.get("templateId")));
        if (templateId == null) {
            throw new ServiceException("预算表ID不能为空");
        }
        Object idsObj = body.get("itemIds");
        List<Long> itemIds = new ArrayList<>();
        if (idsObj instanceof Collection<?> c) {
            for (Object o : c) {
                itemIds.add(Long.valueOf(String.valueOf(o)));
            }
        }
        if (itemIds.isEmpty()) {
            throw new ServiceException("请至少选择一个要解挂的科目");
        }
        BudgetTemplate tpl = budgetTemplateMapper.selectById(templateId);
        if (tpl == null) {
            throw new ServiceException("预算表不存在");
        }
        int year = tpl.getBudgetYear() == null ? 2027 : tpl.getBudgetYear();
        // 仅删除「属于该表该年度」的挂载行，防止误删其它表/方案的数据
        List<BudgetTemplateItem> rows = budgetTemplateItemMapper.selectList(
            new LambdaQueryWrapper<BudgetTemplateItem>()
                .in(BudgetTemplateItem::getId, itemIds)
                .eq(BudgetTemplateItem::getTemplateId, templateId)
                .eq(BudgetTemplateItem::getBudgetYear, year));
        if (CollUtil.isEmpty(rows)) {
            return R.ok(0);
        }
        List<Long> toDelete = rows.stream().map(BudgetTemplateItem::getId).toList();
        int n = budgetTemplateItemMapper.deleteBatchIds(toDelete);
        return R.ok(n);
    }

    /**
     * 更新预算表挂载科目的属性（公式、排序、是否汇总、是否可编辑、负责部门等）
     */
    @SaCheckPermission(value = {"budget:template:edit"}, mode = SaMode.OR)
    @Log(title = "预算表模板-更新科目", businessType = BusinessType.UPDATE)
    @PutMapping("/item")
    public R<Void> updateItem(@RequestBody BudgetTemplateItem item) {
        if (item.getId() == null) {
            throw new ServiceException("科目ID不能为空");
        }
        BudgetTemplateItem exist = budgetTemplateItemMapper.selectById(item.getId());
        if (exist == null) {
            throw new ServiceException("科目不存在或已被删除");
        }
        // 安全校验：只能更新挂载行，且必须属于同一预算表
        if (item.getTemplateId() != null && !item.getTemplateId().equals(exist.getTemplateId())) {
            throw new ServiceException("不能跨预算表修改");
        }
        // 只允许更新的字段（防止改编码、改父级等结构字段，结构调整走挂载/解挂）
        BudgetTemplateItem update = new BudgetTemplateItem();
        update.setId(exist.getId());
        update.setFormula(item.getFormula());
        update.setItemOrder(item.getItemOrder());
        update.setIsSummary(item.getIsSummary());
        update.setIsEditable(item.getIsEditable());
        update.setResponsibleDept(item.getResponsibleDept());
        update.setOrgScope(item.getOrgScope());
        budgetTemplateItemMapper.updateById(update);
        return R.ok();
    }
}