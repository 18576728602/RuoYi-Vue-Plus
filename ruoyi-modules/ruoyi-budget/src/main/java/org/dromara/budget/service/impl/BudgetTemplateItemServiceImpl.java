package org.dromara.budget.service.impl;

import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.core.page.PageQuery;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.dromara.budget.domain.bo.BudgetTemplateItemBo;
import org.dromara.budget.domain.vo.BudgetTemplateItemVo;
import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.budget.domain.BudgetSubjectMaster;
import org.dromara.budget.domain.BudgetTemplate;
import org.dromara.budget.mapper.BudgetTemplateItemMapper;
import org.dromara.budget.mapper.BudgetSubjectMasterMapper;
import org.dromara.budget.mapper.BudgetTemplateMapper;
import org.dromara.budget.service.IBudgetTemplateItemService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.system.domain.SysDept;
import org.dromara.system.mapper.SysDeptMapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 预算模板科目Service业务层处理
 *
 * @author Lion Li
 * @date 2026-08-29
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetTemplateItemServiceImpl implements IBudgetTemplateItemService {

    private final BudgetTemplateItemMapper baseMapper;
    private final SysDeptMapper sysDeptMapper;
    private final BudgetSubjectMasterMapper budgetSubjectMasterMapper;
    private final BudgetTemplateMapper budgetTemplateMapper;

    /**
     * 查询预算模板科目
     *
     * @param id 主键
     * @return 预算模板科目
     */
    @Override
    public BudgetTemplateItemVo queryById(Long id){
        return baseMapper.selectVoById(id);
    }

    /**
     * 分页查询预算模板科目列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 预算模板科目分页列表
     */
    @Override
    public TableDataInfo<BudgetTemplateItemVo> queryPageList(BudgetTemplateItemBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BudgetTemplateItem> lqw = buildQueryWrapper(bo);
        Page<BudgetTemplateItemVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的预算模板科目列表
     *
     * @param bo 查询条件
     * @return 预算模板科目列表
     */
    @Override
    public List<BudgetTemplateItemVo> queryList(BudgetTemplateItemBo bo) {
        LambdaQueryWrapper<BudgetTemplateItem> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    @Override
    public TableDataInfo<BudgetTemplateItemVo> queryDisabledPage(Long planId, String templateCode, PageQuery pageQuery) {
        IPage<BudgetTemplateItemVo> result = baseMapper.selectDisabledPage(pageQuery.build(), planId, templateCode);
        return TableDataInfo.build(result);
    }

    @Override
    public Boolean restoreByIds(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return false;
        }
        return baseMapper.restoreByIds(ids) > 0;
    }

    @Override
    public List<BudgetTemplateItem> listByPlanAndTemplate(Long planId, String templateCode) {
        Long targetPlanId = planId != null ? planId : 0L;
        LambdaQueryWrapper<BudgetTemplateItem> lqw = buildPlanScopedWrapper(targetPlanId, templateCode);
        List<BudgetTemplateItem> planItems = baseMapper.selectList(lqw);
        if (CollUtil.isNotEmpty(planItems)) {
            return planItems;
        }
        // 该方案尚未初始化学科，回退基础模板
        return baseMapper.selectList(buildPlanScopedWrapper(0L, templateCode));
    }

    @Override
    public long countByPlan(Long planId) {
        if (planId == null) {
            return 0L;
        }
        return baseMapper.selectCount(Wrappers.<BudgetTemplateItem>lambdaQuery()
            .eq(BudgetTemplateItem::getPlanId, planId));
    }

    @Override
    public List<Map<String, Object>> listCompanies() {
        List<SysDept> allDepts = sysDeptMapper.selectList(null);
        // 参与填报的公司 = 根节点(父为0)的孙级部门（集团下子公司）
        List<SysDept> companies = allDepts.stream()
            .filter(d -> d.getParentId() != null && d.getParentId() != 0)
            .filter(d -> {
                SysDept parent = allDepts.stream()
                    .filter(p -> p.getDeptId().equals(d.getParentId()))
                    .findFirst().orElse(null);
                if (parent == null) return false;
                SysDept grandParent = parent.getParentId() == null ? null
                    : allDepts.stream().filter(g -> g.getDeptId().equals(parent.getParentId())).findFirst().orElse(null);
                return grandParent != null && grandParent.getParentId() != null && grandParent.getParentId() == 0;
            })
            .sorted(Comparator.comparing(SysDept::getOrderNum))
            .collect(Collectors.toList());
        if (companies.isEmpty()) {
            companies = allDepts.stream()
                .filter(d -> d.getParentId() != null && d.getParentId() != 0)
                .filter(d -> d.getDeptCategory() == null || !d.getDeptCategory().contains("D"))
                .sorted(Comparator.comparing(SysDept::getOrderNum))
                .collect(Collectors.toList());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysDept d : companies) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("deptId", d.getDeptId());
            m.put("deptName", d.getDeptName());
            result.add(m);
        }
        return result;
    }

    private LambdaQueryWrapper<BudgetTemplateItem> buildPlanScopedWrapper(Long planId, String templateCode) {
        return Wrappers.<BudgetTemplateItem>lambdaQuery()
            .eq(BudgetTemplateItem::getPlanId, planId)
            .eq(templateCode != null, BudgetTemplateItem::getTemplateCode, templateCode)
            .orderByAsc(BudgetTemplateItem::getTemplateCode)
            .orderByAsc(BudgetTemplateItem::getItemOrder);
    }

    private LambdaQueryWrapper<BudgetTemplateItem> buildQueryWrapper(BudgetTemplateItemBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<BudgetTemplateItem> lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(BudgetTemplateItem::getId);
        lqw.eq(bo.getPlanId() != null, BudgetTemplateItem::getPlanId, bo.getPlanId());
        lqw.eq(StringUtils.isNotBlank(bo.getTemplateCode()), BudgetTemplateItem::getTemplateCode, bo.getTemplateCode());
        lqw.like(StringUtils.isNotBlank(bo.getTemplateName()), BudgetTemplateItem::getTemplateName, bo.getTemplateName());
        lqw.eq(StringUtils.isNotBlank(bo.getItemCode()), BudgetTemplateItem::getItemCode, bo.getItemCode());
        lqw.like(StringUtils.isNotBlank(bo.getItemName()), BudgetTemplateItem::getItemName, bo.getItemName());
        lqw.eq(StringUtils.isNotBlank(bo.getParentCode()), BudgetTemplateItem::getParentCode, bo.getParentCode());
        lqw.eq(bo.getItemLevel() != null, BudgetTemplateItem::getItemLevel, bo.getItemLevel());
        lqw.eq(bo.getItemOrder() != null, BudgetTemplateItem::getItemOrder, bo.getItemOrder());
        lqw.eq(StringUtils.isNotBlank(bo.getResponsibleDept()), BudgetTemplateItem::getResponsibleDept, bo.getResponsibleDept());
        lqw.eq(bo.getIsSummary() != null, BudgetTemplateItem::getIsSummary, bo.getIsSummary());
        lqw.eq(bo.getIsEditable() != null, BudgetTemplateItem::getIsEditable, bo.getIsEditable());
        lqw.eq(StringUtils.isNotBlank(bo.getFormula()), BudgetTemplateItem::getFormula, bo.getFormula());
        return lqw;
    }

    /**
     * 从科目主数据库导入科目到指定方案（复制挂接，自动补全缺失祖先链，已存在则跳过）
     *
     * @param planId     目标方案ID
     * @param subjectIds 选中的科目主数据ID集合
     * @return 本次实际新增的方案科目数量
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importFromMaster(Long planId, Collection<Long> subjectIds) {
        if (planId == null) {
            throw new ServiceException("方案ID不能为空");
        }
        if (CollUtil.isEmpty(subjectIds)) {
            throw new ServiceException("请至少选择一个科目");
        }
        List<BudgetSubjectMaster> masters = budgetSubjectMasterMapper.selectList(Wrappers.<BudgetSubjectMaster>lambdaQuery()
            .in(BudgetSubjectMaster::getId, subjectIds)
            .eq(BudgetSubjectMaster::getValidFlag, "1"));
        if (CollUtil.isEmpty(masters)) {
            return 0;
        }
        // 全量主数据做 code->master 索引，用于补全祖先
        List<BudgetSubjectMaster> all = budgetSubjectMasterMapper.selectList(Wrappers.<BudgetSubjectMaster>lambdaQuery()
            .eq(BudgetSubjectMaster::getValidFlag, "1"));
        Map<String, BudgetSubjectMaster> byCode = all.stream()
            .collect(Collectors.toMap(BudgetSubjectMaster::getSubjectCode, m -> m, (a, b) -> a));

        // 祖先闭包：确保父级也一并导入，方案科目树完整
        Set<Long> targetIds = new LinkedHashSet<>();
        for (BudgetSubjectMaster m : masters) {
            BudgetSubjectMaster cur = m;
            while (cur != null) {
                if (!targetIds.add(cur.getId())) {
                    break;
                }
                cur = StringUtils.isNotBlank(cur.getParentCode()) ? byCode.get(cur.getParentCode()) : null;
            }
        }
        List<BudgetSubjectMaster> toCopy = all.stream()
            .filter(m -> targetIds.contains(m.getId()))
            .sorted(Comparator.comparingInt(a -> a.getLevel() == null ? 1 : a.getLevel()))
            .toList();
        if (toCopy.isEmpty()) {
            return 0;
        }

        // 方案内已存在判重
        Set<String> existKeys = new HashSet<>();
        List<BudgetTemplateItem> exist = baseMapper.selectList(Wrappers.<BudgetTemplateItem>lambdaQuery()
            .eq(BudgetTemplateItem::getPlanId, planId));
        for (BudgetTemplateItem e : exist) {
            existKeys.add((e.getTemplateCode() == null ? "" : e.getTemplateCode()) + "|" + e.getItemCode());
        }

        // 预算表编码 -> 表名，用于补全 template_name（非空列）
        Map<String, String> tplNames = budgetTemplateMapper.selectList(Wrappers.<BudgetTemplate>lambdaQuery())
            .stream()
            .filter(t -> t.getTemplateCode() != null)
            .collect(Collectors.toMap(BudgetTemplate::getTemplateCode, BudgetTemplate::getTemplateName, (a, b) -> a));

        List<BudgetTemplateItem> inserts = new ArrayList<>();
        for (BudgetSubjectMaster m : toCopy) {
            String tplCode = m.getTemplateCode() == null ? "" : m.getTemplateCode();
            String key = tplCode + "|" + m.getSubjectCode();
            if (existKeys.contains(key)) {
                continue;
            }
            existKeys.add(key);
            BudgetTemplateItem it = new BudgetTemplateItem();
            it.setPlanId(planId);
            it.setRefSubjectId(m.getId());
            it.setTemplateCode(m.getTemplateCode());
            it.setTemplateName(tplNames.getOrDefault(tplCode, ""));
            it.setItemCode(m.getSubjectCode());
            it.setItemName(m.getSubjectName());
            it.setParentCode(m.getParentCode());
            it.setItemLevel(m.getLevel() == null ? 1L : Long.valueOf(m.getLevel()));
            it.setItemOrder(m.getSort() == null ? 1L : Long.valueOf(m.getSort()));
            // 从主数据带汇总/可编辑口径：汇总行(is_summary=1)只读，明细(is_summary=0)可填报
            it.setIsEditable((m.getIsSummary() != null && m.getIsSummary() == 1) ? 0L : 1L);
            it.setIsSummary(m.getIsSummary() == null ? 0L : Long.valueOf(m.getIsSummary()));
            it.setOrgScope(m.getOrgScope());
            inserts.add(it);
        }
        if (CollUtil.isNotEmpty(inserts)) {
            baseMapper.insertBatch(inserts);
        }
        return inserts.size();
    }

    /**
     * 新增预算模板科目
     *
     * @param bo 预算模板科目
     * @return 是否新增成功
     */
    @Override
    public Boolean insertByBo(BudgetTemplateItemBo bo) {
        BudgetTemplateItem add = MapstructUtils.convert(bo, BudgetTemplateItem.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改预算模板科目
     *
     * @param bo 预算模板科目
     * @return 是否修改成功
     */
    @Override
    public Boolean updateByBo(BudgetTemplateItemBo bo) {
        BudgetTemplateItem update = MapstructUtils.convert(bo, BudgetTemplateItem.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(BudgetTemplateItem entity){
        // template_name 为非空列，保存前按 template_code 回填表名，避免 NOT NULL 报错
        if (entity != null && StringUtils.isBlank(entity.getTemplateName()) && StringUtils.isNotBlank(entity.getTemplateCode())) {
            BudgetTemplate t = budgetTemplateMapper.selectOne(Wrappers.<BudgetTemplate>lambdaQuery()
                .eq(BudgetTemplate::getTemplateCode, entity.getTemplateCode())
                .last("limit 1"));
            if (t != null) {
                entity.setTemplateName(t.getTemplateName());
            }
        }
    }

    /**
     * 校验并批量删除预算模板科目信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if(isValid){
            //TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteByIds(ids) > 0;
    }
}
