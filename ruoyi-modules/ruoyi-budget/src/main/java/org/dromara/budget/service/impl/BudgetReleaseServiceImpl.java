package org.dromara.budget.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.*;
import org.dromara.budget.mapper.*;
import org.dromara.budget.service.IBudgetDataVersionService;
import org.dromara.budget.service.IBudgetReleaseService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 终审·正式发布 Service实现
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetReleaseServiceImpl implements IBudgetReleaseService {

    private final BudgetDataMapper budgetDataMapper;
    private final BudgetPlanMapper budgetPlanMapper;
    private final BudgetTemplateItemMapper budgetTemplateItemMapper;
    private final BudgetOperationLogMapper budgetOperationLogMapper;
    private final IBudgetDataVersionService versionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String publish(Long planId, Long deptId, String templateCode) {
        if (planId == null || deptId == null) {
            throw new ServiceException("正式发布需指定预算方案与单位");
        }

        // 1. 校验该范围内数据均为已审批通过（终审前提）
        Long total = budgetDataMapper.selectCount(new LambdaQueryWrapper<BudgetData>()
            .eq(BudgetData::getPlanId, planId)
            .eq(BudgetData::getDeptId, deptId)
            .eq(StrUtil.isNotBlank(templateCode), BudgetData::getTemplateCode, templateCode)
            .ne(BudgetData::getStatus, "APPROVED"));
        if (total != null && total > 0) {
            throw new ServiceException("存在未审核通过的数据，无法正式发布，请先完成全部终审");
        }

        // 2. 计算正式版本号：首个 V1.0，之后（调整后）V1.1...
        int seq = versionService.nextReleaseSeq(planId, deptId, templateCode);
        String releaseNo = "V1." + (seq - 1);

        // 3. 冻结：APPROVED → PUBLISHED
        LambdaUpdateWrapper<BudgetData> uw = new LambdaUpdateWrapper<>();
        uw.eq(BudgetData::getPlanId, planId)
            .eq(BudgetData::getDeptId, deptId)
            .eq(BudgetData::getStatus, "APPROVED")
            .set(BudgetData::getStatus, "PUBLISHED");
        if (StrUtil.isNotBlank(templateCode)) {
            uw.eq(BudgetData::getTemplateCode, templateCode);
        }
        budgetDataMapper.update(null, uw);

        // 4. 生成正式发布(RELEASE)快照
        int inserted = versionService.releaseSnapshot(planId, deptId, templateCode, releaseNo, seq);
        if (inserted == 0) {
            throw new ServiceException("未找到可发布的数据");
        }

        // 5. 留痕
        writeLog(planId, deptId, templateCode, releaseNo);
        return releaseNo;
    }

    @Override
    public Integer currentReleaseSeq(Long planId, Long deptId, String templateCode) {
        return versionService.nextReleaseSeq(planId, deptId, templateCode) - 1;
    }

    private void writeLog(Long planId, Long deptId, String templateCode, String releaseNo) {
        BudgetPlan plan = budgetPlanMapper.selectById(planId);
        BudgetOperationLog logEntity = new BudgetOperationLog();
        logEntity.setPlanId(planId);
        logEntity.setPlanName(plan != null ? plan.getPlanName() : null);
        logEntity.setDeptId(deptId);
        logEntity.setTemplateCode(templateCode);
        logEntity.setTemplateName(templateCode);
        logEntity.setActionType("RELEASE");
        logEntity.setActionLabel("正式发布 " + releaseNo);
        logEntity.setTargetType("FILL");
        logEntity.setOperatorId(LoginHelper.getUserId());
        logEntity.setOperatorName(LoginHelper.getUsername());
        logEntity.setRemark("终审通过后正式发布并冻结数据，正式版本号 " + releaseNo);
        budgetOperationLogMapper.insert(logEntity);
    }
}