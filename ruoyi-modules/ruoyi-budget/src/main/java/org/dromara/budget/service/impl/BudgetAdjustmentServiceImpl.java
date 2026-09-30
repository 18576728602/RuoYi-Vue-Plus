package org.dromara.budget.service;

import org.dromara.budget.domain.bo.AdjustmentApproveBo;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.dromara.budget.domain.bo.BudgetAdjustmentBo;
import org.dromara.budget.domain.vo.BudgetAdjustmentVo;
import org.dromara.budget.domain.vo.BudgetAdjustmentImpactVo;
import org.dromara.budget.domain.BudgetAdjustment;
import org.dromara.budget.domain.BudgetData;
import org.dromara.budget.domain.BudgetPlan;
import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.budget.mapper.BudgetAdjustmentMapper;
import org.dromara.budget.mapper.BudgetDataMapper;
import org.dromara.budget.mapper.BudgetPlanMapper;
import org.dromara.budget.mapper.BudgetTemplateItemMapper;
import org.dromara.budget.service.IBudgetAdjustmentService;
import org.dromara.common.core.exception.ServiceException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.Date;
import java.util.Objects;
import java.util.stream.Collectors;
import cn.hutool.core.collection.CollUtil;

/**
 * 预算调整记录Service业务层处理
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetAdjustmentServiceImpl implements IBudgetAdjustmentService {

    private final BudgetAdjustmentMapper baseMapper;
    private final BudgetDataMapper budgetDataMapper;
    private final BudgetPlanMapper budgetPlanMapper;
    private final BudgetTemplateItemMapper budgetTemplateItemMapper;

    /**
     * 是否为全局用户（集团/国资/超管）；子公司账号仅限本单位
     */
    private boolean isGlobalUser() {
        if (LoginHelper.isSuperAdmin() || LoginHelper.isTenantAdmin()) {
            return true;
        }
        // 集团管理员等 data_scope="1"（全部数据权限）的角色也视为全局，可看集团本部+全部子公司
        try {
            java.util.List<org.dromara.common.core.domain.dto.RoleDTO> roles = LoginHelper.getLoginUser().getRoles();
            if (roles != null) {
                return roles.stream().anyMatch(r -> "1".equals(r.getDataScope()));
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public BudgetAdjustmentVo queryById(Long id){
        return baseMapper.selectVoById(id);
    }

    @Override
    public TableDataInfo<BudgetAdjustmentVo> queryPageList(BudgetAdjustmentBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BudgetAdjustment> lqw = buildQueryWrapper(bo);
        // 数据权限收窄：子公司账号强制限定为本单位调整记录
        if (!isGlobalUser()) {
            lqw.eq(BudgetAdjustment::getOrgId, LoginHelper.getDeptId());
        }
        Page<BudgetAdjustmentVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        fillPlanName(result.getRecords());
        return TableDataInfo.build(result);
    }

    @Override
    public List<BudgetAdjustmentVo> queryList(BudgetAdjustmentBo bo) {
        LambdaQueryWrapper<BudgetAdjustment> lqw = buildQueryWrapper(bo);
        List<BudgetAdjustmentVo> list = baseMapper.selectVoList(lqw);
        fillPlanName(list);
        return list;
    }

    /**
     * 为调整记录列表补充预算方案名称
     */
    private void fillPlanName(List<BudgetAdjustmentVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Set<Long> planIds = list.stream()
            .map(BudgetAdjustmentVo::getPlanId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (planIds.isEmpty()) {
            return;
        }
        Map<Long, String> planNameMap = budgetPlanMapper.selectList(
            new LambdaQueryWrapper<BudgetPlan>().in(BudgetPlan::getId, planIds)
        ).stream().collect(Collectors.toMap(BudgetPlan::getId, BudgetPlan::getPlanName, (a, b) -> a));
        list.forEach(v -> v.setPlanName(planNameMap.get(v.getPlanId())));
    }

    private LambdaQueryWrapper<BudgetAdjustment> buildQueryWrapper(BudgetAdjustmentBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<BudgetAdjustment> lqw = Wrappers.lambdaQuery();
        lqw.orderByDesc(BudgetAdjustment::getCreateTime);
        lqw.eq(bo.getPlanId() != null, BudgetAdjustment::getPlanId, bo.getPlanId());
        lqw.eq(bo.getOrgId() != null, BudgetAdjustment::getOrgId, bo.getOrgId());
        lqw.eq(StringUtils.isNotBlank(bo.getTemplateCode()), BudgetAdjustment::getTemplateCode, bo.getTemplateCode());
        lqw.eq(StringUtils.isNotBlank(bo.getItemCode()), BudgetAdjustment::getItemCode, bo.getItemCode());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), BudgetAdjustment::getStatus, bo.getStatus());
        lqw.like(StringUtils.isNotBlank(bo.getApplicantName()), BudgetAdjustment::getApplicantName, bo.getApplicantName());
        return lqw;
    }

    @Override
    public Boolean insertByBo(BudgetAdjustmentBo bo) {
        // 只有执行中(PUBLISHED)的预算方案才能发起调整；归档/草稿/关闭方案一律禁止
        BudgetPlan plan = budgetPlanMapper.selectById(bo.getPlanId());
        if (plan == null) {
            throw new ServiceException("预算方案不存在");
        }
        if (!"PUBLISHED".equals(plan.getStatus())) {
            throw new ServiceException("该预算方案已归档/停用，不能发起预算调整");
        }
        // 校验：该单位该方案该预算表必须整体已审批通过(APPROVED)，否则禁止调整（整表口径，与前端一致；表内原值为0的漏填科目也可从0调增）
        Long approvedCount = budgetDataMapper.selectCount(new LambdaQueryWrapper<BudgetData>()
            .eq(BudgetData::getPlanId, bo.getPlanId())
            .eq(BudgetData::getDeptId, bo.getOrgId())
            .eq(BudgetData::getTemplateCode, bo.getTemplateCode())
            .eq(BudgetData::getStatus, "APPROVED"));
        if (approvedCount == null || approvedCount == 0) {
            throw new ServiceException("该预算表尚未审批通过，不能发起预算调整");
        }
        // 同一 方案+单位+预算表+科目 下存在待审批调整时，禁止重复申请
        Long pendingCount = baseMapper.selectCount(new LambdaQueryWrapper<BudgetAdjustment>()
            .eq(BudgetAdjustment::getPlanId, bo.getPlanId())
            .eq(BudgetAdjustment::getOrgId, bo.getOrgId())
            .eq(BudgetAdjustment::getTemplateCode, bo.getTemplateCode())
            .eq(BudgetAdjustment::getItemCode, bo.getItemCode())
            .eq(BudgetAdjustment::getStatus, "PENDING"));
        if (pendingCount != null && pendingCount > 0) {
            throw new ServiceException("该单位该预算科目已存在待审批的调整申请，不能重复发起");
        }
        BudgetAdjustment add = MapstructUtils.convert(bo, BudgetAdjustment.class);
        // 新增时默认状态为待审批
        add.setStatus("PENDING");
        // 自动填充申请人信息
        add.setApplicantId(LoginHelper.getUserId());
        add.setApplicantName(LoginHelper.getUsername());
        // 计算调整后金额
        if (add.getOriginalAmount() != null && add.getAdjustAmount() != null) {
            add.setAdjustedAmount(add.getOriginalAmount().add(add.getAdjustAmount()));
        }
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    @Override
    public Boolean updateByBo(BudgetAdjustmentBo bo) {
        BudgetAdjustment update = MapstructUtils.convert(bo, BudgetAdjustment.class);
        // 重新计算调整后金额
        if (update.getOriginalAmount() != null && update.getAdjustAmount() != null) {
            update.setAdjustedAmount(update.getOriginalAmount().add(update.getAdjustAmount()));
        }
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 审批预算调整申请
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean approve(AdjustmentApproveBo bo) {
        BudgetAdjustment adjustment = baseMapper.selectById(bo.getId());
        if (adjustment == null) {
            throw new RuntimeException("调整记录不存在");
        }
        if (!"PENDING".equals(adjustment.getStatus())) {
            throw new RuntimeException("该申请已审批，不可重复审批");
        }

        // 审批时再次校验方案仍处于执行中，避免方案归档后预算被改动
        BudgetPlan plan = budgetPlanMapper.selectById(adjustment.getPlanId());
        if (plan == null || !"PUBLISHED".equals(plan.getStatus())) {
            throw new RuntimeException("该调整关联的方案已归档/关闭，无法审批");
        }

        // 更新审批信息
        adjustment.setStatus(bo.getApproveResult());
        adjustment.setApproverId(LoginHelper.getUserId());
        adjustment.setApproverName(LoginHelper.getUsername());
        adjustment.setApproveTime(new Date());
        adjustment.setApproveRemark(bo.getApproveRemark());
        baseMapper.updateById(adjustment);

        // 如果审批通过，更新对应的预算数据
        if ("APPROVED".equals(bo.getApproveResult())) {
            updateBudgetData(adjustment);
        }

        return true;
    }

    /**
     * 审批通过后更新预算数据
     */
    private void updateBudgetData(BudgetAdjustment adjustment) {
        LambdaQueryWrapper<BudgetData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BudgetData::getPlanId, adjustment.getPlanId())
            .eq(BudgetData::getDeptId, adjustment.getOrgId())
            .eq(BudgetData::getTemplateCode, adjustment.getTemplateCode())
            .eq(BudgetData::getItemCode, adjustment.getItemCode());
        List<BudgetData> dataList = budgetDataMapper.selectList(wrapper);

        if (!dataList.isEmpty()) {
            BudgetData data = dataList.get(0);
            // 用 LambdaUpdateWrapper 显式更新，绕过 @Version 乐观锁，避免静默失败
            budgetDataMapper.update(null, Wrappers.<BudgetData>lambdaUpdate()
                .set(BudgetData::getBudgetAmount, adjustment.getAdjustedAmount())
                .eq(BudgetData::getId, data.getId()));
        }
    }

    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        throw new RuntimeException("预算调整记录一经提交不可删除，只能审批通过或驳回");
    }

    @Override
    public List<String> listApprovedTemplateCodes(Long planId, Long orgId) {
        if (planId == null) {
            return List.of();
        }
        // 子公司账号强制取本单位；集团账号可指定单位
        Long deptId = isGlobalUser() ? orgId : LoginHelper.getDeptId();
        LambdaQueryWrapper<BudgetData> w = Wrappers.<BudgetData>lambdaQuery()
            .select(BudgetData::getTemplateCode)
            .eq(BudgetData::getPlanId, planId)
            .eq(BudgetData::getStatus, "APPROVED")
            .eq(BudgetData::getDelFlag, 0L)
            .groupBy(BudgetData::getTemplateCode);
        if (deptId != null) {
            w.eq(BudgetData::getDeptId, deptId);
        }
        return budgetDataMapper.selectList(w).stream()
            .map(BudgetData::getTemplateCode)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @Override
    public BudgetAdjustmentImpactVo analyzeImpact(Long planId, Long orgId, String templateCode,
                                                  String itemCode, BigDecimal originalAmount,
                                                  BigDecimal adjustAmount) {
        BudgetAdjustmentImpactVo vo = new BudgetAdjustmentImpactVo();
        vo.setItemCode(itemCode);
        vo.setTemplateCode(templateCode);
        vo.setOwnOriginal(nvl(originalAmount));
        vo.setAdjustAmount(adjustAmount != null ? adjustAmount : BigDecimal.ZERO);

        // 基础信息：科目/预算表名称（取方案下科目快照，缺失回退基础模板）
        BudgetTemplateItem item = findTemplateItem(planId, templateCode, itemCode);
        if (item != null) {
            vo.setTemplateName(item.getTemplateName());
            // 明细科目(itemLevel=2)展示末级科目名即可；汇总科目直接用名称
            vo.setItemName(item.getItemName());
        }
        if (StringUtils.isBlank(vo.getTemplateName())) {
            vo.setTemplateName(templateCode);
        }

        // ---- 本公司影响 ----
        // 本表合计 = 该单位该表全部已审批科目预算之和（调整前）
        BigDecimal tableTotalBefore = budgetDataMapper.selectList(new LambdaQueryWrapper<BudgetData>()
                .select(BudgetData::getBudgetAmount)
                .eq(BudgetData::getPlanId, planId)
                .eq(BudgetData::getDeptId, orgId)
                .eq(BudgetData::getTemplateCode, templateCode)
                .eq(BudgetData::getStatus, "APPROVED"))
            .stream().map(BudgetData::getBudgetAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        // 本表合计的调整量：若/当原预算来自本表科目，则+adjust（因该科目已被计入合计）
        BigDecimal tableAdjust = vo.getAdjustAmount();
        BigDecimal tableTotalAfter = tableTotalBefore.add(tableAdjust);
        vo.setTableTotalBefore(tableTotalBefore);
        vo.setTableTotalAfter(tableTotalAfter);
        vo.setTableChangeRate(percent(tableAdjust, tableTotalBefore));

        BigDecimal ownOriginal = vo.getOwnOriginal() != null ? vo.getOwnOriginal() : BigDecimal.ZERO;
        BigDecimal ownAdjusted = ownOriginal.add(vo.getAdjustAmount());
        vo.setOwnAdjusted(ownAdjusted);
        vo.setOwnChangeRate(percent(vo.getAdjustAmount(), ownOriginal));
        // 原预算为0且调整>0时，占比按调整后计算
        BigDecimal shareBase = ownOriginal.signum() > 0 ? ownOriginal : ownAdjusted;
        vo.setOwnShareAfter(percent(shareBase, tableTotalAfter));

        // ---- 集团影响（同科目跨单位汇总） ----
        BigDecimal groupTotalBefore = budgetDataMapper.selectList(new LambdaQueryWrapper<BudgetData>()
                .select(BudgetData::getBudgetAmount)
                .eq(BudgetData::getPlanId, planId)
                .eq(BudgetData::getTemplateCode, templateCode)
                .eq(BudgetData::getItemCode, itemCode)
                .eq(BudgetData::getStatus, "APPROVED"))
            .stream().map(BudgetData::getBudgetAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal groupTotalAfter = groupTotalBefore.add(vo.getAdjustAmount());
        vo.setGroupTotalBefore(groupTotalBefore);
        vo.setGroupTotalAfter(groupTotalAfter);
        vo.setGroupChangeRate(percent(vo.getAdjustAmount(), groupTotalBefore));
        Long companyCount = budgetDataMapper.selectCount(new LambdaQueryWrapper<BudgetData>()
            .select(BudgetData::getDeptId)
            .eq(BudgetData::getPlanId, planId)
            .eq(BudgetData::getTemplateCode, templateCode)
            .eq(BudgetData::getItemCode, itemCode)
            .eq(BudgetData::getStatus, "APPROVED")
            .groupBy(BudgetData::getDeptId));
        vo.setCompanyCount(companyCount == null ? 0L : companyCount);
        vo.setGroupShareAfter(percent(ownAdjusted, groupTotalAfter));

        // ---- 历史调整追溯 ----
        List<BudgetAdjustment> history = baseMapper.selectList(new LambdaQueryWrapper<BudgetAdjustment>()
            .eq(BudgetAdjustment::getPlanId, planId)
            .eq(BudgetAdjustment::getOrgId, orgId)
            .eq(BudgetAdjustment::getTemplateCode, templateCode)
            .eq(BudgetAdjustment::getItemCode, itemCode)
            .orderByDesc(BudgetAdjustment::getCreateTime));
        vo.setHistoryCount((long) history.size());
        vo.setHistoryApprovedCount(history.stream()
            .filter(a -> "APPROVED".equals(a.getStatus())).count());
        vo.setHistoryTotalAmount(history.stream()
            .map(BudgetAdjustment::getAdjustAmount).filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        if (!history.isEmpty()) {
            // createTime 可能为空，倒序遍历找最近一条非空时间
            for (BudgetAdjustment a : history) {
                if (a.getCreateTime() != null) {
                    vo.setLastAdjustDate(a.getCreateTime());
                    break;
                }
            }
        }
        return vo;
    }

    /**
     * 查询方案下科目快照，缺失回退基础模板(planId=0)
     */
    private BudgetTemplateItem findTemplateItem(Long planId, String templateCode, String itemCode) {
        Long targetPlanId = planId != null ? planId : 0L;
        List<BudgetTemplateItem> items = budgetTemplateItemMapper.selectList(new LambdaQueryWrapper<BudgetTemplateItem>()
            .eq(BudgetTemplateItem::getPlanId, targetPlanId)
            .eq(BudgetTemplateItem::getTemplateCode, templateCode)
            .eq(BudgetTemplateItem::getItemCode, itemCode));
        if (CollUtil.isNotEmpty(items)) {
            return items.get(0);
        }
        List<BudgetTemplateItem> fallback = budgetTemplateItemMapper.selectList(new LambdaQueryWrapper<BudgetTemplateItem>()
            .eq(BudgetTemplateItem::getPlanId, 0L)
            .eq(BudgetTemplateItem::getTemplateCode, templateCode)
            .eq(BudgetTemplateItem::getItemCode, itemCode));
        return CollUtil.isNotEmpty(fallback) ? fallback.get(0) : null;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /**
     * 计算 change/denominator 的百分比，分母<=0 时返回0（避免除零）
     */
    private BigDecimal percent(BigDecimal change, BigDecimal denominator) {
        if (change == null || denominator == null || denominator.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return change.multiply(new BigDecimal("100")).divide(denominator, 2, java.math.RoundingMode.HALF_UP);
    }
}