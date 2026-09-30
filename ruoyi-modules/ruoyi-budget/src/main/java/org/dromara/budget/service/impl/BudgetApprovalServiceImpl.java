package org.dromara.budget.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.*;
import org.dromara.budget.mapper.*;
import org.dromara.budget.service.IBudgetApprovalService;
import org.dromara.budget.util.BudgetUnitNameUtil;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.mapper.SysDeptMapper;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.system.domain.SysDept;
import org.dromara.system.domain.SysUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 审批中心 Service实现
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetApprovalServiceImpl implements IBudgetApprovalService {

    private final BudgetDataMapper budgetDataMapper;
    private final BudgetAdjustmentMapper budgetAdjustmentMapper;
    private final BudgetPlanMapper budgetPlanMapper;
    private final SysDeptMapper sysDeptMapper;
    private final SysUserMapper sysUserMapper;
    private final BudgetTemplateItemMapper budgetTemplateItemMapper;
    private final BudgetOperationLogMapper budgetOperationLogMapper;
    private final BudgetApprovalTransferMapper budgetApprovalTransferMapper;
    private final BudgetApprovalCcMapper budgetApprovalCcMapper;
    private final BudgetApprovalRecordMapper budgetApprovalRecordMapper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 是否为全局用户（集团/国资/超管）：可查看全部单位；否则仅限本单位
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

    /**
     * 解析当前用户可见单位范围：
     * 全局账号遵循前端传入的 orgId；普通账号强制限定为本单位，忽略前端传入的其他单位筛选。
     */
    private Long resolveScopeOrg(Long requestedOrgId) {
        if (isGlobalUser()) {
            return requestedOrgId;
        }
        return LoginHelper.getDeptId();
    }

    @Override
    public TableDataInfo<Map<String, Object>> queryPendingList(String type, Long planId, Long orgId, String status, PageQuery pageQuery) {
        List<Map<String, Object>> allList = new ArrayList<>();

        // 数据权限收窄：普通账号（子公司审批人等）强制限定为本单位，忽略下拉传入的其他单位
        Long scopeOrg = resolveScopeOrg(orgId);

        // 默认待审批
        if (StrUtil.isBlank(status)) {
            status = "PENDING";
        }

        // 预加载预算方案名称
        Map<Long, String> planNameMap = new HashMap<>();
        List<BudgetPlan> allPlans = budgetPlanMapper.selectList(null);
        for (BudgetPlan p : allPlans) {
            planNameMap.put(p.getId(), p.getPlanName());
        }

        // 预加载部门名称
        Map<Long, String> deptNameMap = new HashMap<>();
        Map<Long, SysDept> deptMap = new HashMap<>();
        List<SysDept> allDepts = sysDeptMapper.selectList(null);
        for (SysDept dept : allDepts) {
            deptNameMap.put(dept.getDeptId(), dept.getDeptName());
            deptMap.put(dept.getDeptId(), dept);
        }

        // 预加载用户名称（create_by存的是用户ID）
        Map<Long, String> userNameMap = new HashMap<>();
        List<SysUser> allUsers = sysUserMapper.selectList(null);
        for (SysUser user : allUsers) {
            userNameMap.put(user.getUserId(), user.getUserName());
        }

        // 预加载模板名称 + 科目名称映射 + 汇总行标记
        Map<String, String> templateNameMap = new HashMap<>();
        Map<String, String> itemNameMap = new HashMap<>();
        Set<String> summaryItemKeys = new HashSet<>();
        LambdaQueryWrapper<BudgetTemplateItem> tplWrapper = new LambdaQueryWrapper<>();
        List<BudgetTemplateItem> tplList = budgetTemplateItemMapper.selectList(tplWrapper);
        for (BudgetTemplateItem t : tplList) {
            if (!templateNameMap.containsKey(t.getTemplateCode())) {
                templateNameMap.put(t.getTemplateCode(), t.getTemplateName());
            }
            itemNameMap.put(t.getTemplateCode() + "_" + t.getItemCode(), t.getItemName());
            if (t.getIsSummary() != null && t.getIsSummary() == 1L) {
                summaryItemKeys.add(t.getTemplateCode() + "_" + t.getItemCode());
            }
        }

        // 1. 填报数据（从独立审批记录表查询）
        if (StrUtil.isBlank(type) || "FILL".equals(type)) {
            LambdaQueryWrapper<BudgetApprovalRecord> recordWrapper = new LambdaQueryWrapper<>();
            if ("PENDING".equals(status)) {
                recordWrapper.eq(BudgetApprovalRecord::getStatus, "PENDING");
            } else if ("APPROVED".equals(status)) {
                recordWrapper.eq(BudgetApprovalRecord::getStatus, "APPROVED");
            } else if ("REJECTED".equals(status)) {
                recordWrapper.eq(BudgetApprovalRecord::getStatus, "REJECTED");
            }
            if (planId != null) {
                recordWrapper.eq(BudgetApprovalRecord::getPlanId, planId);
            }
            if (scopeOrg != null) {
                recordWrapper.eq(BudgetApprovalRecord::getDeptId, scopeOrg);
            }
            recordWrapper.orderByAsc(BudgetApprovalRecord::getSubmitTime);
            List<BudgetApprovalRecord> records = budgetApprovalRecordMapper.selectList(recordWrapper);

            for (BudgetApprovalRecord record : records) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", record.getId());
                item.put("type", "FILL");
                item.put("typeLabel", "预算填报");
                item.put("planId", record.getPlanId());
                item.put("planName", planNameMap.getOrDefault(record.getPlanId(), ""));
                item.put("deptId", record.getDeptId());
                item.put("deptName", formatDeptName(deptMap, deptNameMap, record.getDeptId()));
                item.put("templateCode", record.getTemplateCode());
                item.put("templateName", record.getTemplateName() != null
                    ? record.getTemplateName() : templateNameMap.getOrDefault(record.getTemplateCode(), ""));
                item.put("itemCount", record.getItemCount() != null ? record.getItemCount() : 0);
                item.put("totalAmount", record.getTotalAmount() != null ? record.getTotalAmount() : BigDecimal.ZERO);
                item.put("status", record.getStatus());
                item.put("submitRound", record.getSubmitRound());
                item.put("submitReason", record.getSubmitReason());
                item.put("approveRemark", record.getApproveRemark());
                item.put("approverName", record.getApproverName());
                item.put("approveTime", record.getApproveTime());
                item.put("createTime", record.getSubmitTime() != null ? record.getSubmitTime() : record.getCreateTime());
                item.put("applicantName", record.getSubmitByName() != null ? record.getSubmitByName() : "-");

                // 明细列表：从BudgetData获取当前数据
                LambdaQueryWrapper<BudgetData> dataWrapper = new LambdaQueryWrapper<>();
                dataWrapper.eq(BudgetData::getPlanId, record.getPlanId())
                    .eq(BudgetData::getDeptId, record.getDeptId())
                    .eq(BudgetData::getTemplateCode, record.getTemplateCode());
                List<BudgetData> dataList = budgetDataMapper.selectList(dataWrapper);
                List<Map<String, Object>> detailList = new ArrayList<>();
                for (BudgetData bd : dataList) {
                    Map<String, Object> detail = new HashMap<>();
                    detail.put("itemCode", bd.getItemCode());
                    detail.put("itemName", itemNameMap.getOrDefault(bd.getTemplateCode() + "_" + bd.getItemCode(), ""));
                    detail.put("budgetAmount", bd.getBudgetAmount());
                    detail.put("lastActual", bd.getLastActual());
                    detail.put("remark", bd.getRemark());
                    detailList.add(detail);
                }
                item.put("detailList", detailList);
                allList.add(item);
            }
        }

        // 2. 调整数据
        if (StrUtil.isBlank(type) || "ADJUSTMENT".equals(type)) {
            LambdaQueryWrapper<BudgetAdjustment> adjWrapper = new LambdaQueryWrapper<>();
            if ("PENDING".equals(status)) {
                adjWrapper.eq(BudgetAdjustment::getStatus, "PENDING");
            } else if ("APPROVED".equals(status)) {
                adjWrapper.eq(BudgetAdjustment::getStatus, "APPROVED");
            } else if ("REJECTED".equals(status)) {
                adjWrapper.eq(BudgetAdjustment::getStatus, "REJECTED");
            }
            if (planId != null) {
                adjWrapper.eq(BudgetAdjustment::getPlanId, planId);
            }
            if (scopeOrg != null) {
                adjWrapper.eq(BudgetAdjustment::getOrgId, scopeOrg);
            }
            List<BudgetAdjustment> adjList = budgetAdjustmentMapper.selectList(adjWrapper);
            for (BudgetAdjustment a : adjList) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", a.getId());
                item.put("type", "ADJUSTMENT");
                item.put("typeLabel", "预算调整");
                item.put("planId", a.getPlanId());
                item.put("planName", planNameMap.getOrDefault(a.getPlanId(), ""));
                item.put("deptId", a.getOrgId());
                item.put("deptName", formatDeptName(deptMap, deptNameMap, a.getOrgId()));
                item.put("templateCode", a.getTemplateCode());
                item.put("templateName", templateNameMap.getOrDefault(a.getTemplateCode(), ""));
                item.put("itemCode", a.getItemCode());
                item.put("itemName", itemNameMap.getOrDefault(a.getTemplateCode() + "_" + a.getItemCode(), ""));
                item.put("originalAmount", a.getOriginalAmount());
                item.put("adjustAmount", a.getAdjustAmount());
                item.put("adjustedAmount", a.getAdjustedAmount());
                item.put("reason", a.getReason());
                item.put("applicantName", a.getApplicantName() != null ? a.getApplicantName() : "-");
                item.put("status", a.getStatus());
                item.put("createTime", a.getCreateTime());

                // 查原始预算数据
                LambdaQueryWrapper<BudgetData> origWrapper = new LambdaQueryWrapper<>();
                origWrapper.eq(BudgetData::getPlanId, a.getPlanId())
                    .eq(BudgetData::getDeptId, a.getOrgId())
                    .eq(BudgetData::getTemplateCode, a.getTemplateCode())
                    .eq(BudgetData::getItemCode, a.getItemCode());
                BudgetData origData = budgetDataMapper.selectOne(origWrapper);

                // 组装调整明细列表（单条科目调整）
                List<Map<String, Object>> adjDetailList = new ArrayList<>();
                Map<String, Object> adjDetail = new HashMap<>();
                adjDetail.put("itemCode", a.getItemCode());
                adjDetail.put("itemName", itemNameMap.getOrDefault(a.getTemplateCode() + "_" + a.getItemCode(), ""));
                adjDetail.put("originalAmount", a.getOriginalAmount());
                adjDetail.put("adjustAmount", a.getAdjustAmount());
                adjDetail.put("adjustedAmount", a.getAdjustedAmount());
                adjDetail.put("lastActual", origData != null ? origData.getLastActual() : null);
                adjDetail.put("reason", a.getReason());
                adjDetailList.add(adjDetail);
                item.put("detailList", adjDetailList);

                allList.add(item);
            }
        }

        // 排序：按提交时间正序（先提交的排前面），时间相同时按模板编码正序（01、02...16）
        allList.sort((a, b) -> {
            Object ta = a.get("createTime");
            Object tb = b.get("createTime");
            if (ta != null && tb != null) {
                int cmp = ((Comparable) ta).compareTo(tb);
                if (cmp != 0) return cmp;
            } else if (ta == null && tb != null) {
                return 1;
            } else if (ta != null) {
                return -1;
            }
            String ca = (String) a.getOrDefault("templateCode", "");
            String cb = (String) b.getOrDefault("templateCode", "");
            return ca.compareTo(cb);
        });

        // 手动分页
        int total = allList.size();
        int current = (int) pageQuery.getPageNum();
        int size = (int) pageQuery.getPageSize();
        int fromIndex = (current - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);
        List<Map<String, Object>> pageList = fromIndex < total
            ? allList.subList(fromIndex, toIndex)
            : new ArrayList<>();

        TableDataInfo<Map<String, Object>> result = TableDataInfo.build(pageList);
        result.setTotal(total);
        return result;
    }

    @Override
    public Map<String, Object> getStats(Long planId, String type, Long orgId) {
        Map<String, Object> stats = new HashMap<>();

        boolean showFill = StrUtil.isBlank(type) || "FILL".equals(type);
        boolean showAdj = StrUtil.isBlank(type) || "ADJUSTMENT".equals(type);

        // 数据权限收窄：普通账号强制限定为本单位
        Long scopeOrg = resolveScopeOrg(orgId);

        // 待审批
        long fillPending = 0, adjPending = 0;
        if (showFill) {
            LambdaQueryWrapper<BudgetApprovalRecord> fillPendingWrapper = new LambdaQueryWrapper<>();
            fillPendingWrapper.eq(BudgetApprovalRecord::getStatus, "PENDING");
            if (planId != null) {
                fillPendingWrapper.eq(BudgetApprovalRecord::getPlanId, planId);
            }
            if (scopeOrg != null) {
                fillPendingWrapper.eq(BudgetApprovalRecord::getDeptId, scopeOrg);
            }
            fillPending = budgetApprovalRecordMapper.selectCount(fillPendingWrapper);
        }
        if (showAdj) {
            LambdaQueryWrapper<BudgetAdjustment> adjPendingWrapper = new LambdaQueryWrapper<>();
            adjPendingWrapper.eq(BudgetAdjustment::getStatus, "PENDING");
            if (planId != null) {
                adjPendingWrapper.eq(BudgetAdjustment::getPlanId, planId);
            }
            if (scopeOrg != null) {
                adjPendingWrapper.eq(BudgetAdjustment::getOrgId, scopeOrg);
            }
            adjPending = budgetAdjustmentMapper.selectCount(adjPendingWrapper);
        }

        // 已通过
        long fillApproved = 0, adjApproved = 0;
        if (showFill) {
            LambdaQueryWrapper<BudgetApprovalRecord> fillApprovedWrapper = new LambdaQueryWrapper<>();
            fillApprovedWrapper.eq(BudgetApprovalRecord::getStatus, "APPROVED");
            if (planId != null) {
                fillApprovedWrapper.eq(BudgetApprovalRecord::getPlanId, planId);
            }
            if (scopeOrg != null) {
                fillApprovedWrapper.eq(BudgetApprovalRecord::getDeptId, scopeOrg);
            }
            fillApproved = budgetApprovalRecordMapper.selectCount(fillApprovedWrapper);
        }
        if (showAdj) {
            LambdaQueryWrapper<BudgetAdjustment> adjApprovedWrapper = new LambdaQueryWrapper<>();
            adjApprovedWrapper.eq(BudgetAdjustment::getStatus, "APPROVED");
            if (planId != null) {
                adjApprovedWrapper.eq(BudgetAdjustment::getPlanId, planId);
            }
            if (scopeOrg != null) {
                adjApprovedWrapper.eq(BudgetAdjustment::getOrgId, scopeOrg);
            }
            adjApproved = budgetAdjustmentMapper.selectCount(adjApprovedWrapper);
        }

        // 已驳回
        long fillRejected = 0, adjRejected = 0;
        if (showFill) {
            LambdaQueryWrapper<BudgetApprovalRecord> fillRejectedWrapper = new LambdaQueryWrapper<>();
            fillRejectedWrapper.eq(BudgetApprovalRecord::getStatus, "REJECTED");
            if (planId != null) {
                fillRejectedWrapper.eq(BudgetApprovalRecord::getPlanId, planId);
            }
            if (scopeOrg != null) {
                fillRejectedWrapper.eq(BudgetApprovalRecord::getDeptId, scopeOrg);
            }
            fillRejected = budgetApprovalRecordMapper.selectCount(fillRejectedWrapper);
        }
        if (showAdj) {
            LambdaQueryWrapper<BudgetAdjustment> adjRejectedWrapper = new LambdaQueryWrapper<>();
            adjRejectedWrapper.eq(BudgetAdjustment::getStatus, "REJECTED");
            if (planId != null) {
                adjRejectedWrapper.eq(BudgetAdjustment::getPlanId, planId);
            }
            if (scopeOrg != null) {
                adjRejectedWrapper.eq(BudgetAdjustment::getOrgId, scopeOrg);
            }
            adjRejected = budgetAdjustmentMapper.selectCount(adjRejectedWrapper);
        }

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        Date todayStart = cal.getTime();

        long fillProcessed = 0, adjProcessed = 0;
        if (showFill) {
            // 从操作日志统计今日处理的填报（包括通过和驳回）
            LambdaQueryWrapper<BudgetOperationLog> fillProcessedTodayWrapper = new LambdaQueryWrapper<>();
            fillProcessedTodayWrapper.in(BudgetOperationLog::getActionType, "APPROVE", "REJECT");
            fillProcessedTodayWrapper.eq(BudgetOperationLog::getTargetType, "FILL");
            fillProcessedTodayWrapper.ge(BudgetOperationLog::getCreateTime, todayStart);
            if (planId != null) {
                fillProcessedTodayWrapper.eq(BudgetOperationLog::getPlanId, planId);
            }
            if (scopeOrg != null) {
                fillProcessedTodayWrapper.eq(BudgetOperationLog::getDeptId, scopeOrg);
            }
            fillProcessed = budgetOperationLogMapper.selectCount(fillProcessedTodayWrapper);
        }
        if (showAdj) {
            LambdaQueryWrapper<BudgetAdjustment> adjProcessedTodayWrapper = new LambdaQueryWrapper<>();
            adjProcessedTodayWrapper.in(BudgetAdjustment::getStatus, "APPROVED", "REJECTED");
            adjProcessedTodayWrapper.ge(BudgetAdjustment::getApproveTime, todayStart);
            if (planId != null) {
                adjProcessedTodayWrapper.eq(BudgetAdjustment::getPlanId, planId);
            }
            if (scopeOrg != null) {
                adjProcessedTodayWrapper.eq(BudgetAdjustment::getOrgId, scopeOrg);
            }
            adjProcessed = budgetAdjustmentMapper.selectCount(adjProcessedTodayWrapper);
        }

        stats.put("fillPending", fillPending);
        stats.put("adjPending", adjPending);
        stats.put("totalPending", fillPending + adjPending);
        stats.put("totalApproved", fillApproved + adjApproved);
        stats.put("totalRejected", fillRejected + adjRejected);
        stats.put("processedToday", fillProcessed + adjProcessed);

        return stats;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchApprove(List<Long> ids, String type, String action, String remark) {
        if (CollUtil.isEmpty(ids)) {
            throw new ServiceException("请选择审批项");
        }
        // 按审批类型强校验权限：填报(FILL)→budget:approval:approve；调整(ADJUSTMENT)→budget:adjustment:approve
        checkApproveTypePermission(type);
        Long approverId = LoginHelper.getUserId();
        String approverName = LoginHelper.getUsername();
        Date now = new Date();

        if ("FILL".equals(type)) {
            // 按审批记录ID查询待审批记录
            LambdaQueryWrapper<BudgetApprovalRecord> recordWrapper = new LambdaQueryWrapper<>();
            recordWrapper.in(BudgetApprovalRecord::getId, ids);
            recordWrapper.eq(BudgetApprovalRecord::getStatus, "PENDING");
            List<BudgetApprovalRecord> records = budgetApprovalRecordMapper.selectList(recordWrapper);

            for (BudgetApprovalRecord record : records) {
                // 更新审批记录状态
                LambdaUpdateWrapper<BudgetApprovalRecord> recordUpdate = new LambdaUpdateWrapper<>();
                recordUpdate.eq(BudgetApprovalRecord::getId, record.getId());
                if ("APPROVED".equals(action)) {
                    recordUpdate.set(BudgetApprovalRecord::getStatus, "APPROVED");
                } else {
                    recordUpdate.set(BudgetApprovalRecord::getStatus, "REJECTED");
                }
                recordUpdate.set(BudgetApprovalRecord::getApproverId, approverId);
                recordUpdate.set(BudgetApprovalRecord::getApproverName, approverName);
                recordUpdate.set(BudgetApprovalRecord::getApproveTime, now);
                recordUpdate.set(BudgetApprovalRecord::getApproveRemark, remark);
                budgetApprovalRecordMapper.update(null, recordUpdate);

                // 同步更新BudgetData状态（保持数据状态一致性）
                LambdaUpdateWrapper<BudgetData> dataUpdate = new LambdaUpdateWrapper<>();
                dataUpdate.eq(BudgetData::getPlanId, record.getPlanId())
                    .eq(BudgetData::getDeptId, record.getDeptId())
                    .eq(BudgetData::getTemplateCode, record.getTemplateCode())
                    .eq(BudgetData::getStatus, "SUBMITTED");
                if ("APPROVED".equals(action)) {
                    dataUpdate.set(BudgetData::getStatus, "APPROVED");
                    dataUpdate.set(BudgetData::getRemark, null);
                } else {
                    dataUpdate.set(BudgetData::getStatus, "DRAFT");
                    dataUpdate.set(BudgetData::getRemark, "【驳回】" + (StringUtils.isNotBlank(remark) ? remark : "无"));
                }
                budgetDataMapper.update(null, dataUpdate);

                // 写操作日志
                if ("APPROVED".equals(action)) {
                    writeOperationLog(record.getPlanId(), record.getDeptId(), record.getTemplateCode(),
                        "APPROVE", "审批通过", "FILL", null);
                } else {
                    writeOperationLog(record.getPlanId(), record.getDeptId(), record.getTemplateCode(),
                        "REJECT", "审批驳回", "FILL", remark);
                }
            }
        } else if ("ADJUSTMENT".equals(type)) {
            LambdaQueryWrapper<BudgetAdjustment> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(BudgetAdjustment::getId, ids);
            wrapper.eq(BudgetAdjustment::getStatus, "PENDING");
            List<BudgetAdjustment> list = budgetAdjustmentMapper.selectList(wrapper);
            for (BudgetAdjustment a : list) {
                // 审批时校验方案仍处于执行中，避免方案归档后预算被改动
                BudgetPlan plan = budgetPlanMapper.selectById(a.getPlanId());
                if (plan == null || !"PUBLISHED".equals(plan.getStatus())) {
                    throw new ServiceException("存在调整关联的方案已归档/关闭，无法审批，请先归档前处理待审批调整");
                }
                BudgetAdjustment update = new BudgetAdjustment();
                update.setId(a.getId());
                update.setStatus(action);
                update.setApproverId(approverId);
                update.setApproverName(approverName);
                update.setApproveTime(now);
                update.setApproveRemark(remark);
                budgetAdjustmentMapper.updateById(update);

                if ("APPROVED".equals(action)) {
                    updateBudgetData(a);
                }

                // 写一条调整审批操作日志
                writeOperationLog(a.getPlanId(), a.getOrgId(), a.getTemplateCode(),
                    "APPROVED".equals(action) ? "APPROVE" : "REJECT",
                    "APPROVED".equals(action) ? "调整审批通过" : "调整审批驳回", "ADJUSTMENT", remark);
            }
        }
    }

    @Override
    public List<Map<String, Object>> getTrace(String type, Long planId, Long deptId, String templateCode) {
        List<Map<String, Object>> nodes = new ArrayList<>();

        // 1. 操作日志节点（提交/通过/驳回）
        String targetType = StrUtil.isBlank(type) ? null : type;
        if (StrUtil.isBlank(targetType) || "FILL".equals(targetType)) {
            LambdaQueryWrapper<BudgetOperationLog> opWrap = new LambdaQueryWrapper<>();
            opWrap.eq(BudgetOperationLog::getDeptId, deptId);
            if (planId != null) {
                opWrap.eq(BudgetOperationLog::getPlanId, planId);
            }
            if (StrUtil.isNotBlank(templateCode)) {
                opWrap.eq(BudgetOperationLog::getTemplateCode, templateCode);
            }
            opWrap.eq(BudgetOperationLog::getTargetType, "FILL");
            opWrap.orderByAsc(BudgetOperationLog::getCreateTime);
            for (BudgetOperationLog op : budgetOperationLogMapper.selectList(opWrap)) {
                nodes.add(traceNode(actionNodeLabel(op.getActionType()), op.getOperatorName(), op.getRemark(),
                    op.getCreateTime(), op.getActionType()));
            }
        }
        if (StrUtil.isBlank(targetType) || "ADJUSTMENT".equals(targetType)) {
            LambdaQueryWrapper<BudgetOperationLog> opWrap = new LambdaQueryWrapper<>();
            opWrap.eq(BudgetOperationLog::getDeptId, deptId);
            if (planId != null) {
                opWrap.eq(BudgetOperationLog::getPlanId, planId);
            }
            if (StrUtil.isNotBlank(templateCode)) {
                opWrap.eq(BudgetOperationLog::getTemplateCode, templateCode);
            }
            opWrap.eq(BudgetOperationLog::getTargetType, "ADJUSTMENT");
            opWrap.orderByAsc(BudgetOperationLog::getCreateTime);
            for (BudgetOperationLog op : budgetOperationLogMapper.selectList(opWrap)) {
                nodes.add(traceNode(actionNodeLabel(op.getActionType()), op.getOperatorName(), op.getRemark(),
                    op.getCreateTime(), op.getActionType()));
            }
        }

        // 2. 转交记录节点
        LambdaQueryWrapper<BudgetApprovalTransfer> tfWrap = new LambdaQueryWrapper<>();
        tfWrap.eq(BudgetApprovalTransfer::getDeptId, deptId);
        if (planId != null) {
            tfWrap.eq(BudgetApprovalTransfer::getPlanId, planId);
        }
        if (StrUtil.isNotBlank(templateCode)) {
            tfWrap.eq(BudgetApprovalTransfer::getTemplateCode, templateCode);
        }
        if (StrUtil.isNotBlank(targetType)) {
            tfWrap.eq(BudgetApprovalTransfer::getTargetType, targetType);
        }
        for (BudgetApprovalTransfer tf : budgetApprovalTransferMapper.selectList(tfWrap)) {
            Map<String, Object> node = new HashMap<>();
            node.put("label", "审批转交");
            node.put("operatorName", (tf.getTransfererName() != null ? tf.getTransfererName() : "") + " → " + (tf.getReceiverName() != null ? tf.getReceiverName() : ""));
            node.put("remark", tf.getReason());
            node.put("time", tf.getTransferTime() != null ? tf.getTransferTime() : tf.getCreateTime());
            node.put("actionType", "TRANSFER");
            nodes.add(node);
        }

        // 3. 按时间排序
        nodes.sort(Comparator.comparing(n -> (Date) n.get("time"), Comparator.nullsLast(Comparator.naturalOrder())));
        return nodes;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long planId, Long deptId, String templateCode, String type, Long receiverId, String reason) {
        if (receiverId == null) {
            throw new ServiceException("请选择接收人");
        }
        if (deptId == null) {
            throw new ServiceException("待办单位缺失，无法转交");
        }
        String targetType = StrUtil.isBlank(type) ? "FILL" : type;
        Long transfererId = LoginHelper.getUserId();
        String transfererName = LoginHelper.getUsername();

        SysUser receiver = sysUserMapper.selectById(receiverId);
        if (receiver == null) {
            throw new ServiceException("接收人不存在");
        }
        if (receiverId.equals(transfererId)) {
            throw new ServiceException("不能转交给本人");
        }

        BudgetApprovalTransfer tf = new BudgetApprovalTransfer();
        tf.setTargetType(targetType);
        tf.setPlanId(planId);
        tf.setDeptId(deptId);
        tf.setTemplateCode(templateCode);
        tf.setTransfererId(transfererId);
        tf.setTransfererName(transfererName);
        tf.setReceiverId(receiverId);
        tf.setReceiverName(receiver.getUserName());
        tf.setReason(StrUtil.isBlank(reason) ? "转交办理" : reason);
        tf.setTransferTime(new Date());
        budgetApprovalTransferMapper.insert(tf);

        // 留痕到操作日志
        List<BudgetTemplateItem> tpl = budgetTemplateItemMapper.selectList(
            new LambdaQueryWrapper<BudgetTemplateItem>().eq(BudgetTemplateItem::getPlanId, 0L)
                .eq(BudgetTemplateItem::getTemplateCode, templateCode).last("LIMIT 1"));
        BudgetOperationLog log = new BudgetOperationLog();
        log.setPlanId(planId);
        log.setDeptId(deptId);
        log.setTemplateCode(templateCode);
        log.setPlanName(resolvePlanName(planId));
        log.setTemplateName(CollUtil.isNotEmpty(tpl) ? tpl.get(0).getTemplateName() : templateCode);
        log.setActionType("TRANSFER");
        log.setActionLabel("审批转交");
        log.setTargetType(targetType);
        log.setOperatorId(transfererId);
        log.setOperatorName(transfererName);
        log.setRemark(transfererName + " 转交至 " + receiver.getUserName() + (StrUtil.isNotBlank(tf.getReason()) ? "：" + tf.getReason() : ""));
        budgetOperationLogMapper.insert(log);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cc(Long planId, Long deptId, String templateCode, String type, List<Long> receiverIds, String reason) {
        if (CollUtil.isEmpty(receiverIds)) {
            throw new ServiceException("请选择抄送人");
        }
        if (deptId == null) {
            throw new ServiceException("待办单位缺失，无法抄送");
        }
        String targetType = StrUtil.isBlank(type) ? "FILL" : type;
        Long senderId = LoginHelper.getUserId();
        String senderName = LoginHelper.getUsername();
        Date now = new Date();

        List<BudgetApprovalCc> records = new ArrayList<>();
        for (Long rid : receiverIds) {
            if (rid == null || rid.equals(senderId)) {
                continue;
            }
            SysUser receiver = sysUserMapper.selectById(rid);
            if (receiver == null) {
                continue;
            }
            BudgetApprovalCc cc = new BudgetApprovalCc();
            cc.setTargetType(targetType);
            cc.setPlanId(planId);
            cc.setDeptId(deptId);
            cc.setTemplateCode(templateCode);
            cc.setSenderId(senderId);
            cc.setSenderName(senderName);
            cc.setReceiverId(rid);
            cc.setReceiverName(receiver.getUserName());
            cc.setReason(StrUtil.isBlank(reason) ? "知悉备忘" : reason);
            cc.setIsRead(0);
            cc.setCcTime(now);
            records.add(cc);
        }
        if (CollUtil.isEmpty(records)) {
            throw new ServiceException("无有效抄送人（已排除本人或不存在的用户）");
        }
        for (BudgetApprovalCc cc : records) {
            budgetApprovalCcMapper.insert(cc);
        }

        // 留痕到操作日志
        List<BudgetTemplateItem> tpl = budgetTemplateItemMapper.selectList(
            new LambdaQueryWrapper<BudgetTemplateItem>().eq(BudgetTemplateItem::getPlanId, 0L)
                .eq(BudgetTemplateItem::getTemplateCode, templateCode).last("LIMIT 1"));
        BudgetOperationLog log = new BudgetOperationLog();
        log.setPlanId(planId);
        log.setDeptId(deptId);
        log.setTemplateCode(templateCode);
        log.setPlanName(resolvePlanName(planId));
        log.setTemplateName(CollUtil.isNotEmpty(tpl) ? tpl.get(0).getTemplateName() : templateCode);
        log.setActionType("CC");
        log.setActionLabel("审批抄送");
        log.setTargetType(targetType);
        log.setOperatorId(senderId);
        log.setOperatorName(senderName);
        log.setRemark(senderName + " 抄送 " + records.stream()
            .map(BudgetApprovalCc::getReceiverName).filter(Objects::nonNull).collect(Collectors.joining("、")));
        budgetOperationLogMapper.insert(log);
    }

    @Override
    public TableDataInfo<Map<String, Object>> queryMyCcList(String type, PageQuery pageQuery) {
        Long currentUserId = LoginHelper.getUserId();
        LambdaQueryWrapper<BudgetApprovalCc> qw = new LambdaQueryWrapper<>();
        qw.eq(BudgetApprovalCc::getReceiverId, currentUserId);
        if (StrUtil.isNotBlank(type)) {
            qw.eq(BudgetApprovalCc::getTargetType, type);
        }
        qw.orderByDesc(BudgetApprovalCc::getCcTime, BudgetApprovalCc::getCreateTime);
        qw.last("LIMIT 200");
        List<BudgetApprovalCc> ccList = budgetApprovalCcMapper.selectList(qw);

        Map<Long, String> planNameMap = new HashMap<>();
        List<BudgetPlan> allPlans = budgetPlanMapper.selectList(null);
        for (BudgetPlan p : allPlans) {
            planNameMap.put(p.getId(), p.getPlanName());
        }
        Map<Long, SysDept> deptMap = new HashMap<>();
        List<SysDept> allDepts = sysDeptMapper.selectList(null);
        for (SysDept dept : allDepts) {
            deptMap.put(dept.getDeptId(), dept);
        }
        Map<Long, String> deptNameMap = new HashMap<>();
        for (SysDept dept : allDepts) {
            deptNameMap.put(dept.getDeptId(), dept.getDeptName());
        }
        Map<String, String> templateNameMap = new HashMap<>();
        List<BudgetTemplateItem> tplList = budgetTemplateItemMapper.selectList(null);
        for (BudgetTemplateItem t : tplList) {
            if (!templateNameMap.containsKey(t.getTemplateCode())) {
                templateNameMap.put(t.getTemplateCode(), t.getTemplateName());
            }
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (BudgetApprovalCc cc : ccList) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", cc.getId());
            item.put("type", cc.getTargetType());
            item.put("typeLabel", "FILL".equals(cc.getTargetType()) ? "预算填报" : "预算调整");
            item.put("planId", cc.getPlanId());
            item.put("planName", planNameMap.getOrDefault(cc.getPlanId(), ""));
            item.put("deptId", cc.getDeptId());
            item.put("deptName", formatDeptName(deptMap, deptNameMap, cc.getDeptId()));
            item.put("templateCode", cc.getTemplateCode());
            item.put("templateName", templateNameMap.getOrDefault(cc.getTemplateCode(), ""));
            item.put("senderName", cc.getSenderName());
            item.put("reason", cc.getReason());
            item.put("isRead", cc.getIsRead());
            item.put("ccTime", cc.getCcTime() != null ? cc.getCcTime() : cc.getCreateTime());
            list.add(item);
        }
        int total = list.size();
        int current = (int) pageQuery.getPageNum();
        int size = (int) pageQuery.getPageSize();
        int fromIndex = (current - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);
        List<Map<String, Object>> pageList = fromIndex < total ? list.subList(fromIndex, toIndex) : new ArrayList<>();
        TableDataInfo<Map<String, Object>> result = TableDataInfo.build(pageList);
        result.setTotal(total);
        return result;
    }

    @Override
    public void markCcRead(Long ccId) {
        if (ccId == null) {
            return;
        }
        BudgetApprovalCc cc = budgetApprovalCcMapper.selectById(ccId);
        if (cc == null || Integer.valueOf(1).equals(cc.getIsRead())) {
            return;
        }
        BudgetApprovalCc update = new BudgetApprovalCc();
        update.setId(ccId);
        update.setIsRead(1);
        update.setReadTime(new Date());
        budgetApprovalCcMapper.updateById(update);
    }

    @Override
    public Map<String, Object> getEfficiency(Long planId, String type, Long orgId) {
        Map<String, Object> result = new HashMap<>();
        Long scopeOrg = resolveScopeOrg(orgId);

        List<BudgetOperationLog> logs = budgetOperationLogMapper.selectList(
            new LambdaQueryWrapper<BudgetOperationLog>()
                .eq(scopeOrg != null, BudgetOperationLog::getDeptId, scopeOrg)
                .eq(planId != null, BudgetOperationLog::getPlanId, planId)
                .in(BudgetOperationLog::getActionType, "SUBMIT", "APPROVE", "REJECT"));

        // 按 (类型,单位,表) 配对：首个SUBMIT作为开始，首个APPROVE/REJECT作为结束
        Map<String, Date> submitMap = new HashMap<>();
        Map<String, Long> durationMap = new HashMap<>();
        for (BudgetOperationLog op : logs) {
            String key = (planId != null ? planId : op.getPlanId()) + "_" + op.getDeptId() + "_" + op.getTemplateCode() + "_" + op.getTargetType();
            if ("SUBMIT".equals(op.getActionType())) {
                submitMap.putIfAbsent(key, op.getCreateTime());
            } else if ("APPROVE".equals(op.getActionType()) || "REJECT".equals(op.getActionType())) {
                Date submit = submitMap.get(key);
                if (submit != null && op.getCreateTime() != null) {
                    long hours = Math.max(0, (op.getCreateTime().getTime() - submit.getTime()) / 3600_000L);
                    durationMap.merge(key + ":" + op.getTargetType(), hours, Math::max);
                }
            }
        }
        double fillSum = 0, adjSum = 0;
        int fillCnt = 0, adjCnt = 0;
        for (Map.Entry<String, Long> e : durationMap.entrySet()) {
            String key = e.getKey();
            long hours = e.getValue();
            if (key.endsWith(":ADJUSTMENT")) {
                adjSum += hours;
                adjCnt++;
            } else {
                fillSum += hours;
                fillCnt++;
            }
        }
        // 已办填报/已办调整：直接从操作日志统计APPROVE+REJECT次数（不依赖配对）
        long fillDoneCnt = logs.stream()
            .filter(l -> "FILL".equals(l.getTargetType()))
            .filter(l -> "APPROVE".equals(l.getActionType()) || "REJECT".equals(l.getActionType()))
            .count();
        long adjDoneCnt = logs.stream()
            .filter(l -> "ADJUSTMENT".equals(l.getTargetType()))
            .filter(l -> "APPROVE".equals(l.getActionType()) || "REJECT".equals(l.getActionType()))
            .count();
        result.put("fillAvgHours", fillCnt > 0 ? Math.round(fillSum / fillCnt * 10.0) / 10.0 : 0);
        result.put("adjAvgHours", adjCnt > 0 ? Math.round(adjSum / adjCnt * 10.0) / 10.0 : 0);
        result.put("fillApprovedCnt", fillDoneCnt);
        result.put("adjApprovedCnt", adjDoneCnt);

        // 当前待办滞留时长（最长待办小时数）
        Date now = new Date();
        long maxPendHours = 0;
        long maxAdjPendHours = 0;
        if (StrUtil.isBlank(type) || "FILL".equals(type)) {
            LambdaQueryWrapper<BudgetApprovalRecord> fw = new LambdaQueryWrapper<>();
            fw.eq(BudgetApprovalRecord::getStatus, "PENDING");
            if (planId != null) fw.eq(BudgetApprovalRecord::getPlanId, planId);
            if (scopeOrg != null) fw.eq(BudgetApprovalRecord::getDeptId, scopeOrg);
            for (BudgetApprovalRecord r : budgetApprovalRecordMapper.selectList(fw)) {
                Date refTime = r.getSubmitTime() != null ? r.getSubmitTime() : r.getCreateTime();
                if (refTime != null) {
                    long h = Math.max(0, (now.getTime() - refTime.getTime()) / 3600_000L);
                    maxPendHours = Math.max(maxPendHours, h);
                }
            }
        }
        if (StrUtil.isBlank(type) || "ADJUSTMENT".equals(type)) {
            LambdaQueryWrapper<BudgetAdjustment> aw = new LambdaQueryWrapper<>();
            aw.eq(BudgetAdjustment::getStatus, "PENDING");
            if (planId != null) aw.eq(BudgetAdjustment::getPlanId, planId);
            if (scopeOrg != null) aw.eq(BudgetAdjustment::getOrgId, scopeOrg);
            for (BudgetAdjustment a : budgetAdjustmentMapper.selectList(aw)) {
                if (a.getCreateTime() != null) {
                    long h = Math.max(0, (now.getTime() - a.getCreateTime().getTime()) / 3600_000L);
                    maxAdjPendHours = Math.max(maxAdjPendHours, h);
                }
            }
        }
        result.put("maxPendHours", maxPendHours);
        result.put("maxAdjPendHours", maxAdjPendHours);
        return result;
    }

    private Map<String, Object> traceNode(String label, String operatorName, String remark, Date time, String actionType) {
        Map<String, Object> node = new HashMap<>();
        node.put("label", label);
        node.put("operatorName", operatorName != null ? operatorName : "-");
        node.put("remark", remark);
        node.put("time", time);
        node.put("actionType", actionType);
        return node;
    }

    private String actionNodeLabel(String actionType) {
        if ("SUBMIT".equals(actionType)) return "提交审批";
        if ("APPROVE".equals(actionType)) return "审批通过";
        if ("REJECT".equals(actionType)) return "审批驳回";
        if ("SAVE_DRAFT".equals(actionType)) return "保存草稿";
        if ("TRANSFER".equals(actionType)) return "审批转交";
        if ("CC".equals(actionType)) return "审批抄送";
        if ("ADJUST".equals(actionType)) return "调整审批";
        return actionType;
    }

    /**
     * 按审批类型校验当前用户权限：
     * 填报(FILL)→budget:approval:approve；预算调整(ADJUSTMENT)→budget:adjustment:approve。
     * 未匹配到对应类型权限时抛出无权限异常。
     */
    private void checkApproveTypePermission(String type) {
        java.util.Set<String> perms = LoginHelper.getLoginUser().getMenuPermission();
        if (perms == null || perms.contains("*:*:*")) {
            return;
        }
        if ("ADJUSTMENT".equals(type)) {
            if (!perms.contains("budget:adjustment:approve")) {
                throw new ServiceException("暂无预算调整审批权限");
            }
        } else {
            if (!perms.contains("budget:approval:approve")) {
                throw new ServiceException("暂无预算填报(编制)审批权限");
            }
        }
    }

    /**
     * 按填报单位显示名口径格式化部门名
     */
    private String formatDeptName(Map<Long, SysDept> deptMap, Map<Long, String> deptNameMap, Long deptId) {
        SysDept dept = deptId == null ? null : deptMap.get(deptId);
        if (dept == null) {
            return deptNameMap.getOrDefault(deptId, "未知单位");
        }
        SysDept parent = dept.getParentId() == null ? null : deptMap.get(dept.getParentId());
        return BudgetUnitNameUtil.format(dept.getDeptName(), parent != null ? parent.getDeptName() : null);
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

    private void updateBudgetData(BudgetAdjustment adj) {
        LambdaQueryWrapper<BudgetData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BudgetData::getPlanId, adj.getPlanId())
            .eq(BudgetData::getDeptId, adj.getOrgId())
            .eq(BudgetData::getTemplateCode, adj.getTemplateCode())
            .eq(BudgetData::getItemCode, adj.getItemCode());
        List<BudgetData> list = budgetDataMapper.selectList(wrapper);
        if (!list.isEmpty()) {
            BudgetData data = list.get(0);
            BudgetData update = new BudgetData();
            update.setId(data.getId());
            update.setBudgetAmount(adj.getAdjustedAmount());
            budgetDataMapper.updateById(update);
        }
    }

    @Override
    public List<Map<String, Object>> listCcCandidates(Long excludeUserId) {
        Long currentUserId = LoginHelper.getUserId();

        // 查当前用户所属部门及其祖先部门（限制转交范围：本公司 + 集团本部）
        String deptSql = "SELECT dept_id, ancestors FROM sys_dept WHERE dept_id = " +
            "(SELECT dept_id FROM sys_user WHERE user_id = ?)";
        Map<String, Object> deptRow = jdbcTemplate.queryForMap(deptSql, currentUserId);
        Long currentDeptId = ((Number) deptRow.get("dept_id")).longValue();
        String ancestors = (String) deptRow.get("ancestors");
        // 可转交的部门范围：本公司 + 所有祖先部门（集团）
        List<Long> scopeDeptIds = new ArrayList<>();
        scopeDeptIds.add(currentDeptId);
        if (StrUtil.isNotBlank(ancestors)) {
            for (String s : ancestors.split(",")) {
                if (StrUtil.isNotBlank(s)) {
                    scopeDeptIds.add(Long.valueOf(s.trim()));
                }
            }
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DISTINCT u.user_id, u.user_name, u.nick_name, u.dept_id ");
        sql.append("FROM sys_user u ");
        sql.append("JOIN sys_user_role ur ON u.user_id = ur.user_id ");
        sql.append("JOIN sys_role_menu rm ON ur.role_id = rm.role_id ");
        sql.append("JOIN sys_menu m ON rm.menu_id = m.menu_id ");
        sql.append("WHERE m.perms = 'budget:approval:approve' ");
        sql.append("AND u.status = '0' AND u.del_flag = '0' ");
        sql.append("AND u.user_id != 1 ");
        sql.append("AND u.user_id != ? ");
        List<Object> params = new ArrayList<>();
        params.add(currentUserId);
        if (excludeUserId != null) {
            sql.append("AND u.user_id != ? ");
            params.add(excludeUserId);
        }
        // 限制部门范围
        sql.append("AND u.dept_id IN (");
        for (int i = 0; i < scopeDeptIds.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
            params.add(scopeDeptIds.get(i));
        }
        sql.append(") ");
        sql.append("ORDER BY u.nick_name");

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }
}
