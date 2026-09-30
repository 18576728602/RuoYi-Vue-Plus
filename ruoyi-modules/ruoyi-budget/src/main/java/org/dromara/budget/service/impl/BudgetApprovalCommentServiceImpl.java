package org.dromara.budget.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.BudgetApprovalComment;
import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.budget.domain.bo.BudgetApprovalCommentBo;
import org.dromara.budget.domain.vo.BudgetApprovalCommentVo;
import org.dromara.budget.mapper.BudgetApprovalCommentMapper;
import org.dromara.budget.mapper.BudgetTemplateItemMapper;
import org.dromara.budget.service.IBudgetApprovalCommentService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批批注 Service实现
 *
 * <p>PRD 8.2 单元格级批注。</p>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetApprovalCommentServiceImpl implements IBudgetApprovalCommentService {

    private final BudgetApprovalCommentMapper commentMapper;
    private final BudgetTemplateItemMapper templateItemMapper;
    private final org.dromara.budget.mapper.BudgetApprovalRecordMapper budgetApprovalRecordMapper;

    @Override
    public BudgetApprovalCommentVo addComment(BudgetApprovalCommentBo bo) {
        Long currentUserId = LoginHelper.getUserId();

        // 解析关联审批记录ID：优先使用BO传入的recordId，否则自动查找最新PENDING记录
        Long recordId = bo.getRecordId();
        if (recordId == null) {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<org.dromara.budget.domain.BudgetApprovalRecord> rw =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            rw.eq(org.dromara.budget.domain.BudgetApprovalRecord::getPlanId, bo.getPlanId())
                .eq(org.dromara.budget.domain.BudgetApprovalRecord::getDeptId, bo.getDeptId())
                .eq(org.dromara.budget.domain.BudgetApprovalRecord::getTemplateCode, bo.getTemplateCode())
                .eq(org.dromara.budget.domain.BudgetApprovalRecord::getStatus, "PENDING")
                .orderByDesc(org.dromara.budget.domain.BudgetApprovalRecord::getSubmitRound)
                .last("LIMIT 1");
            org.dromara.budget.domain.BudgetApprovalRecord latestRecord = budgetApprovalRecordMapper.selectOne(rw);
            if (latestRecord != null) {
                recordId = latestRecord.getId();
            }
        }

        BudgetApprovalComment comment = new BudgetApprovalComment();
        comment.setRecordId(recordId);
        comment.setTargetType(bo.getTargetType());
        comment.setPlanId(bo.getPlanId());
        comment.setDeptId(bo.getDeptId());
        comment.setTemplateCode(bo.getTemplateCode());
        comment.setItemCode(bo.getItemCode());
        comment.setCommentType(bo.getCommentType());
        comment.setContent(bo.getContent());
        comment.setCommenterId(LoginHelper.getUserId());
        comment.setCommenterName(LoginHelper.getUsername());
        comment.setStatus("PENDING");
        commentMapper.insert(comment);
        return decorate(comment);
    }

    @Override
    public List<BudgetApprovalCommentVo> listByScope(String targetType, Long planId, Long deptId, String templateCode, String itemCode) {
        return listByScopeWithRecord(targetType, planId, deptId, templateCode, itemCode, null);
    }

    @Override
    public List<BudgetApprovalCommentVo> listByScopeWithRecord(String targetType, Long planId, Long deptId, String templateCode, String itemCode, Long recordId) {
        LambdaQueryWrapper<BudgetApprovalComment> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(targetType)) {
            wrapper.eq(BudgetApprovalComment::getTargetType, targetType);
        }
        if (planId != null) {
            wrapper.eq(BudgetApprovalComment::getPlanId, planId);
        }
        if (deptId != null) {
            wrapper.eq(BudgetApprovalComment::getDeptId, deptId);
        }
        if (StrUtil.isNotBlank(templateCode)) {
            wrapper.eq(BudgetApprovalComment::getTemplateCode, templateCode);
        }
        if (StrUtil.isNotBlank(itemCode)) {
            wrapper.eq(BudgetApprovalComment::getItemCode, itemCode);
        }
        if (recordId != null) {
            wrapper.eq(BudgetApprovalComment::getRecordId, recordId);
        }
        wrapper.orderByAsc(BudgetApprovalComment::getCreateTime);
        List<BudgetApprovalComment> list = commentMapper.selectList(wrapper);

        // 科目名称映射（预算表编号+科目编码）
        Map<String, String> itemNameMap = new HashMap<>();
        List<BudgetTemplateItem> tpl = templateItemMapper.selectList(new LambdaQueryWrapper<BudgetTemplateItem>()
            .eq(BudgetTemplateItem::getPlanId, 0L));
        for (BudgetTemplateItem t : tpl) {
            itemNameMap.put(t.getTemplateCode() + "_" + t.getItemCode(), t.getItemName());
        }

        // 审批记录映射（recordId -> 审批记录信息）
        Map<Long, org.dromara.budget.domain.BudgetApprovalRecord> recordMap = new HashMap<>();
        if (!list.isEmpty()) {
            List<Long> recordIds = list.stream()
                .map(BudgetApprovalComment::getRecordId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
            if (!recordIds.isEmpty()) {
                // 使用 in 查询替代 selectBatchIds，避免租户插件或主键策略问题
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<org.dromara.budget.domain.BudgetApprovalRecord> rw =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                rw.in(org.dromara.budget.domain.BudgetApprovalRecord::getId, recordIds);
                List<org.dromara.budget.domain.BudgetApprovalRecord> records = budgetApprovalRecordMapper.selectList(rw);
                if (records != null) {
                    for (org.dromara.budget.domain.BudgetApprovalRecord r : records) {
                        recordMap.put(r.getId(), r);
                    }
                }
            }
        }

        List<BudgetApprovalCommentVo> result = new ArrayList<>();
        for (BudgetApprovalComment c : list) {
            BudgetApprovalCommentVo vo = decorate(c, itemNameMap);
            // 填充审批记录信息
            if (c.getRecordId() != null && recordMap.containsKey(c.getRecordId())) {
                org.dromara.budget.domain.BudgetApprovalRecord record = recordMap.get(c.getRecordId());
                vo.setSubmitRound(record.getSubmitRound());
                vo.setRecordStatus(record.getStatus());
                vo.setRecordSubmitTime(record.getSubmitTime());
                vo.setRecordApproverName(record.getApproverName());
                vo.setRecordApproveTime(record.getApproveTime());
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public BudgetApprovalCommentVo reply(Long id, String replyContent, String status) {
        if (StrUtil.isBlank(replyContent)) {
            throw new ServiceException("回复内容不能为空");
        }
        BudgetApprovalComment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new ServiceException("批注不存在或已删除");
        }
        LambdaUpdateWrapper<BudgetApprovalComment> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(BudgetApprovalComment::getId, id);
        wrapper.set(BudgetApprovalComment::getReplyContent, replyContent);
        wrapper.set(BudgetApprovalComment::getReplierId, LoginHelper.getUserId());
        wrapper.set(BudgetApprovalComment::getReplierName, LoginHelper.getUsername());
        wrapper.set(BudgetApprovalComment::getReplyTime, new Date());
        // 有回复即视为已处理；支持显式传入 HANDLED/ACCEPTED
        wrapper.set(BudgetApprovalComment::getStatus, StrUtil.isNotBlank(status) ? status : "HANDLED");
        commentMapper.update(null, wrapper);
        comment = commentMapper.selectById(id);
        return decorate(comment);
    }

    @Override
    public BudgetApprovalCommentVo handle(Long id, String status) {
        BudgetApprovalComment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new ServiceException("批注不存在或已删除");
        }
        LambdaUpdateWrapper<BudgetApprovalComment> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(BudgetApprovalComment::getId, id);
        wrapper.set(BudgetApprovalComment::getStatus, StrUtil.isNotBlank(status) ? status : "HANDLED");
        commentMapper.update(null, wrapper);
        comment = commentMapper.selectById(id);
        return decorate(comment);
    }

    @Override
    public void remove(Long id) {
        commentMapper.deleteById(id);
    }

    private BudgetApprovalCommentVo decorate(BudgetApprovalComment c) {
        return decorate(c, null);
    }

    private BudgetApprovalCommentVo decorate(BudgetApprovalComment c, Map<String, String> itemNameMap) {
        BudgetApprovalCommentVo vo = new BudgetApprovalCommentVo();
        vo.setId(c.getId());
        vo.setRecordId(c.getRecordId());
        vo.setTargetType(c.getTargetType());
        vo.setPlanId(c.getPlanId());
        vo.setDeptId(c.getDeptId());
        vo.setTemplateCode(c.getTemplateCode());
        vo.setItemCode(c.getItemCode());
        vo.setCommentType(c.getCommentType());
        vo.setContent(c.getContent());
        vo.setCommenterId(c.getCommenterId());
        vo.setCommenterName(c.getCommenterName());
        vo.setReplyContent(c.getReplyContent());
        vo.setReplierId(c.getReplierId());
        vo.setReplierName(c.getReplierName());
        vo.setReplyTime(c.getReplyTime());
        vo.setStatus(c.getStatus());
        vo.setCreateTime(c.getCreateTime());
        vo.setCommentTypeLabel(commentTypeText(c.getCommentType()));
        vo.setStatusLabel(statusText(c.getStatus()));
        if (itemNameMap != null && c.getItemCode() != null) {
            vo.setItemName(itemNameMap.getOrDefault(c.getTemplateCode() + "_" + c.getItemCode(), ""));
        }
        return vo;
    }

    private String commentTypeText(String type) {
        if ("SUGGESTION".equals(type)) return "建议修改";
        if ("QUESTION".equals(type)) return "疑问";
        if ("WARNING".equals(type)) return "警告";
        return type;
    }

    private String statusText(String status) {
        if ("PENDING".equals(status)) return "待处理";
        if ("HANDLED".equals(status)) return "已处理";
        if ("ACCEPTED".equals(status)) return "已采纳";
        return status;
    }
}