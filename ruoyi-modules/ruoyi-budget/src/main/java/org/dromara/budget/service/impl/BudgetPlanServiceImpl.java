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
import org.springframework.transaction.annotation.Transactional;
import org.dromara.budget.domain.bo.BudgetPlanBo;
import org.dromara.budget.domain.vo.BudgetPlanVo;
import org.dromara.budget.domain.BudgetPlan;
import org.dromara.budget.domain.BudgetData;
import org.dromara.budget.domain.BudgetAdjustment;
import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.budget.mapper.BudgetPlanMapper;
import org.dromara.budget.mapper.BudgetDataMapper;
import org.dromara.budget.mapper.BudgetAdjustmentMapper;
import org.dromara.budget.mapper.BudgetTemplateItemMapper;
import org.dromara.budget.service.IBudgetPlanService;
import org.dromara.common.core.exception.ServiceException;
import cn.hutool.core.collection.CollUtil;

import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Date;

/**
 * 预算方案Service业务层处理
 *
 * @author Lion Li
 * @date 2026-08-29
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetPlanServiceImpl implements IBudgetPlanService {

    private final BudgetPlanMapper baseMapper;
    private final BudgetDataMapper budgetDataMapper;
    private final BudgetAdjustmentMapper budgetAdjustmentMapper;
    private final BudgetTemplateItemMapper budgetTemplateItemMapper;

    /**
     * 查询预算方案
     *
     * @param id 主键
     * @return 预算方案
     */
    @Override
    public BudgetPlanVo queryById(Long id){
        return baseMapper.selectVoById(id);
    }

    /**
     * 分页查询预算方案列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 预算方案分页列表
     */
    @Override
    public TableDataInfo<BudgetPlanVo> queryPageList(BudgetPlanBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BudgetPlan> lqw = buildQueryWrapper(bo);
        Page<BudgetPlanVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的预算方案列表
     *
     * @param bo 查询条件
     * @return 预算方案列表
     */
    @Override
    public List<BudgetPlanVo> queryList(BudgetPlanBo bo) {
        LambdaQueryWrapper<BudgetPlan> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<BudgetPlan> buildQueryWrapper(BudgetPlanBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<BudgetPlan> lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(BudgetPlan::getId);
        lqw.eq(StringUtils.isNotBlank(bo.getPlanCode()), BudgetPlan::getPlanCode, bo.getPlanCode());
        lqw.like(StringUtils.isNotBlank(bo.getPlanName()), BudgetPlan::getPlanName, bo.getPlanName());
        lqw.eq(bo.getBudgetYear() != null, BudgetPlan::getBudgetYear, bo.getBudgetYear());
        lqw.eq(bo.getStartDate() != null, BudgetPlan::getStartDate, bo.getStartDate());
        lqw.eq(bo.getEndDate() != null, BudgetPlan::getEndDate, bo.getEndDate());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), BudgetPlan::getStatus, bo.getStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getApprovalFlow()), BudgetPlan::getApprovalFlow, bo.getApprovalFlow());
        lqw.eq(StringUtils.isNotBlank(bo.getDescription()), BudgetPlan::getDescription, bo.getDescription());
        return lqw;
    }

    /**
     * 新增预算方案
     *
     * @param bo 预算方案
     * @return 是否新增成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertByBo(BudgetPlanBo bo) {
        BudgetPlan add = MapstructUtils.convert(bo, BudgetPlan.class);
        validEntityBeforeSave(add);
        if (add.getStatus() != null && "PUBLISHED".equals(add.getStatus())) {
            validatePublishedUniqueness(add.getBudgetYear(), null);
        }
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
            // 方案科目不再自动整批复制，由用户在方案页「从科目库导入」勾选主数据科目后复制挂接
        }
        return flag ? add.getId() : null;
    }

    /**
     * 初始化方案科目集（案:D2 默认从上一年度/最近有科目方案复制，否则复制基础模板 plan_id=0）。
     * 复制为 plan_id=本方案、from_plan_id=来源方案；本方案自定义新增科目留空。
     */
    @Transactional(rollbackFor = Exception.class)
    public void initPlanItems(Long newPlanId, Long newYear) {
        if (newPlanId == null) {
            return;
        }
        // 已初始化则跳过
        Long exist = budgetTemplateItemMapper.selectCount(Wrappers.<BudgetTemplateItem>lambdaQuery()
            .eq(BudgetTemplateItem::getPlanId, newPlanId));
        if (exist != null && exist > 0) {
            return;
        }
        // 定位来源：优先最近一个"有科目快照"的往期方案，否则基础模板
        List<BudgetPlan> priorPlans = new ArrayList<>();
        if (newYear != null) {
            priorPlans = baseMapper.selectList(Wrappers.<BudgetPlan>lambdaQuery()
                .lt(BudgetPlan::getBudgetYear, newYear)
                .orderByDesc(BudgetPlan::getBudgetYear));
        }
        List<BudgetTemplateItem> source = new ArrayList<>();
        Long fromPlanId = null;
        for (BudgetPlan p : priorPlans) {
            Long cnt = budgetTemplateItemMapper.selectCount(Wrappers.<BudgetTemplateItem>lambdaQuery()
                .eq(BudgetTemplateItem::getPlanId, p.getId()));
            if (cnt != null && cnt > 0) {
                source = budgetTemplateItemMapper.selectList(Wrappers.<BudgetTemplateItem>lambdaQuery()
                    .eq(BudgetTemplateItem::getPlanId, p.getId()));
                fromPlanId = p.getId();
                break;
            }
        }
        if (CollUtil.isEmpty(source)) {
            source = budgetTemplateItemMapper.selectList(Wrappers.<BudgetTemplateItem>lambdaQuery()
                .eq(BudgetTemplateItem::getPlanId, 0L));
            fromPlanId = null;
        }
        if (CollUtil.isEmpty(source)) {
            return;
        }
        Long finalFromPlanId = fromPlanId;
        List<BudgetTemplateItem> copies = source.stream().map(s -> {
            BudgetTemplateItem c = new BudgetTemplateItem();
            c.setPlanId(newPlanId);
            c.setFromPlanId(finalFromPlanId);
            // 主数据引用式：继承来源行的挂接关系，主数据变更可自动同步到本方案
            c.setRefSubjectId(s.getRefSubjectId());
            c.setTemplateCode(s.getTemplateCode());
            c.setTemplateName(s.getTemplateName());
            c.setItemCode(s.getItemCode());
            c.setItemName(s.getItemName());
            c.setParentCode(s.getParentCode());
            c.setItemLevel(s.getItemLevel());
            c.setItemOrder(s.getItemOrder());
            c.setResponsibleDept(s.getResponsibleDept());
            c.setIsSummary(s.getIsSummary());
            c.setIsEditable(s.getIsEditable());
            c.setFormula(s.getFormula());
            return c;
        }).toList();
        if (CollUtil.isNotEmpty(copies)) {
            budgetTemplateItemMapper.insertBatch(copies);
        }
    }

    /**
     * 修改预算方案
     *
     * @param bo 预算方案
     * @return 是否修改成功
     */
    @Override
    public Boolean updateByBo(BudgetPlanBo bo) {
        BudgetPlan existing = baseMapper.selectById(bo.getId());
        if (existing == null) {
            throw new ServiceException("预算方案不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new ServiceException("该方案已发布/归档/关闭，只能查看，不能修改");
        }
        BudgetPlan update = MapstructUtils.convert(bo, BudgetPlan.class);
        validEntityBeforeSave(update);
        // 发布时校验同一年度只能有一个执行中方案
        if (update.getStatus() != null && "PUBLISHED".equals(update.getStatus())) {
            validatePublishedUniqueness(update.getBudgetYear() != null ? update.getBudgetYear() : existing.getBudgetYear(), existing.getId());
        }
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 校验同一年度不能存在两个执行中(PUBLISHED)方案
     */
    private void validatePublishedUniqueness(Long budgetYear, Long excludeId) {
        if (budgetYear == null) {
            return;
        }
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<BudgetPlan>()
            .eq(BudgetPlan::getBudgetYear, budgetYear)
            .eq(BudgetPlan::getStatus, "PUBLISHED")
            .ne(excludeId != null, BudgetPlan::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException("该预算年度已存在执行中的预算方案，不能重复发布");
        }
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(BudgetPlan entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 变更方案状态（发布/归档/关闭）
     * 发布：仅草稿→已发布，同一年度唯一
     * 归档：仅已发布→已归档，且须已到达填报截止时间
     * 关闭：仅已发布/已归档→已关闭
     */
    @Override
    public void changeStatus(BudgetPlanBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("缺少方案ID");
        }
        BudgetPlan existing = baseMapper.selectById(bo.getId());
        if (existing == null) {
            throw new ServiceException("预算方案不存在");
        }
        String target = bo.getStatus();
        if (target == null) {
            throw new ServiceException("缺少目标状态");
        }
        switch (target) {
            case "PUBLISHED" -> {
                if (!"DRAFT".equals(existing.getStatus())) {
                    throw new ServiceException("仅草稿状态的方案可发布");
                }
                validatePublishedUniqueness(existing.getBudgetYear(), existing.getId());
                existing.setStatus("PUBLISHED");
            }
            case "ARCHIVED" -> {
                if (!"PUBLISHED".equals(existing.getStatus())) {
                    throw new ServiceException("仅已发布的方案可归档");
                }
                // 归档须在填报截止时间之后
                if (existing.getEndDate() != null && new Date().before(existing.getEndDate())) {
                    throw new ServiceException("方案填报截止时间未到，不能归档");
                }
                existing.setStatus("ARCHIVED");
            }
            case "CLOSED" -> {
                if (!"PUBLISHED".equals(existing.getStatus()) && !"ARCHIVED".equals(existing.getStatus())) {
                    throw new ServiceException("仅已发布或已归档的方案可关闭");
                }
                existing.setStatus("CLOSED");
            }
            default -> throw new ServiceException("不支持的状态变更: " + target);
        }
        baseMapper.updateById(existing);
    }

    /**
     * 校验并批量删除预算方案信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            for (Long id : ids) {
                BudgetPlan plan = baseMapper.selectById(id);
                if (plan == null) continue;
                if (!"DRAFT".equals(plan.getStatus())) {
                    throw new ServiceException("只有草稿状态的预算方案才能删除");
                }
                long dataCnt = budgetDataMapper.selectCount(Wrappers.<BudgetData>lambdaQuery().eq(BudgetData::getPlanId, id));
                long adjCnt = budgetAdjustmentMapper.selectCount(Wrappers.<BudgetAdjustment>lambdaQuery().eq(BudgetAdjustment::getPlanId, id));
                if (dataCnt > 0 || adjCnt > 0) {
                    throw new ServiceException("该方案已存在填报或调整数据，不能删除");
                }
            }
        }
        return baseMapper.deleteByIds(ids) > 0;
    }
}
