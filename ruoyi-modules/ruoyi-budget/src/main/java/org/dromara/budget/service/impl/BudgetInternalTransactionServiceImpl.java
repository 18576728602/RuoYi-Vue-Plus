package org.dromara.budget.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.BudgetEliminationConfig;
import org.dromara.budget.domain.BudgetInternalTransaction;
import org.dromara.budget.mapper.BudgetEliminationConfigMapper;
import org.dromara.budget.mapper.BudgetInternalTransactionMapper;
import org.dromara.budget.service.IBudgetInternalTransactionService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.domain.SysDept;
import org.dromara.system.mapper.SysDeptMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 内部交易登记 Service实现
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetInternalTransactionServiceImpl implements IBudgetInternalTransactionService {

    private final BudgetInternalTransactionMapper transactionMapper;
    private final BudgetEliminationConfigMapper configMapper;
    private final SysDeptMapper sysDeptMapper;

    @Override
    public TableDataInfo<Map<String, Object>> queryList(Long planId, String transactionType, String status, PageQuery pageQuery) {
        LambdaQueryWrapper<BudgetInternalTransaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(planId != null, BudgetInternalTransaction::getPlanId, planId);
        wrapper.eq(StrUtil.isNotBlank(transactionType), BudgetInternalTransaction::getTransactionType, transactionType);
        wrapper.eq(StrUtil.isNotBlank(status), BudgetInternalTransaction::getStatus, status);
        wrapper.orderByDesc(BudgetInternalTransaction::getCreateTime);

        List<BudgetInternalTransaction> list = transactionMapper.selectList(wrapper);

        // 预加载部门名称
        Map<Long, String> deptNameMap = new HashMap<>();
        List<SysDept> allDepts = sysDeptMapper.selectList(null);
        for (SysDept dept : allDepts) {
            deptNameMap.put(dept.getDeptId(), dept.getDeptName());
        }

        // 预加载抵销项目名称
        Map<String, String> projectNameMap = new HashMap<>();
        LambdaQueryWrapper<BudgetEliminationConfig> configWrapper = new LambdaQueryWrapper<>();
        List<BudgetEliminationConfig> configs = configMapper.selectList(configWrapper);
        for (BudgetEliminationConfig config : configs) {
            projectNameMap.put(config.getProjectCode(), config.getProjectName());
        }

        // 组装结果
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (BudgetInternalTransaction t : list) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", t.getId());
            map.put("planId", t.getPlanId());
            map.put("transactionType", t.getTransactionType());
            map.put("fromDeptId", t.getFromDeptId());
            map.put("fromDeptName", deptNameMap.getOrDefault(t.getFromDeptId(), "未知"));
            map.put("toDeptId", t.getToDeptId());
            map.put("toDeptName", deptNameMap.getOrDefault(t.getToDeptId(), "未知"));
            map.put("eliminationProject", t.getEliminationProject());
            map.put("projectName", projectNameMap.getOrDefault(t.getEliminationProject(), t.getEliminationProject()));
            map.put("amount", t.getAmount());
            map.put("transactionDate", t.getTransactionDate());
            map.put("remark", t.getRemark());
            map.put("status", t.getStatus());
            map.put("createTime", t.getCreateTime());
            resultList.add(map);
        }

        // 手动分页
        int total = resultList.size();
        int current = pageQuery.getPageNum() != null ? pageQuery.getPageNum() : 1;
        int size = pageQuery.getPageSize() != null ? pageQuery.getPageSize() : 10;
        int fromIndex = (current - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);
        List<Map<String, Object>> pageList = fromIndex < total ? resultList.subList(fromIndex, toIndex) : new ArrayList<>();

        TableDataInfo<Map<String, Object>> result = TableDataInfo.build(pageList);
        result.setTotal(total);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insert(Map<String, Object> params) {
        Long planId = Long.valueOf(params.get("planId").toString());
        String transactionType = params.get("transactionType").toString();
        Long fromDeptId = Long.valueOf(params.get("fromDeptId").toString());
        Long toDeptId = Long.valueOf(params.get("toDeptId").toString());
        String eliminationProject = params.get("eliminationProject").toString();
        BigDecimal amount = new BigDecimal(params.get("amount").toString());

        // 校验
        if (fromDeptId.equals(toDeptId)) {
            throw new ServiceException("付款方和收款方不能是同一单位");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("交易金额必须大于0");
        }

        BudgetInternalTransaction transaction = new BudgetInternalTransaction();
        transaction.setPlanId(planId);
        transaction.setTransactionType(transactionType);
        transaction.setFromDeptId(fromDeptId);
        transaction.setToDeptId(toDeptId);
        transaction.setEliminationProject(eliminationProject);
        transaction.setAmount(amount);
        if (params.get("transactionDate") != null && StrUtil.isNotBlank(params.get("transactionDate").toString())) {
            try {
                transaction.setTransactionDate(new java.text.SimpleDateFormat("yyyy-MM-dd").parse(params.get("transactionDate").toString()));
            } catch (Exception ignored) {
            }
        }
        transaction.setRemark(params.get("remark") != null ? params.get("remark").toString() : null);
        transaction.setStatus("DRAFT");
        transaction.setCreateBy(LoginHelper.getUserId());
        transaction.setCreateTime(new Date());

        return transactionMapper.insert(transaction) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean update(Map<String, Object> params) {
        Long id = Long.valueOf(params.get("id").toString());

        BudgetInternalTransaction existing = transactionMapper.selectById(id);
        if (existing == null) {
            throw new ServiceException("记录不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new ServiceException("只有草稿状态的记录才能修改");
        }

        Long fromDeptId = Long.valueOf(params.get("fromDeptId").toString());
        Long toDeptId = Long.valueOf(params.get("toDeptId").toString());
        BigDecimal amount = new BigDecimal(params.get("amount").toString());

        if (fromDeptId.equals(toDeptId)) {
            throw new ServiceException("付款方和收款方不能是同一单位");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("交易金额必须大于0");
        }

        LambdaUpdateWrapper<BudgetInternalTransaction> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(BudgetInternalTransaction::getId, id);
        wrapper.set(BudgetInternalTransaction::getFromDeptId, fromDeptId);
        wrapper.set(BudgetInternalTransaction::getToDeptId, toDeptId);
        wrapper.set(BudgetInternalTransaction::getEliminationProject, params.get("eliminationProject").toString());
        wrapper.set(BudgetInternalTransaction::getAmount, amount);
        if (params.get("transactionDate") != null && StrUtil.isNotBlank(params.get("transactionDate").toString())) {
            try {
                wrapper.set(BudgetInternalTransaction::getTransactionDate, new java.text.SimpleDateFormat("yyyy-MM-dd").parse(params.get("transactionDate").toString()));
            } catch (Exception ignored) {
            }
        }
        wrapper.set(BudgetInternalTransaction::getRemark, params.get("remark") != null ? params.get("remark").toString() : null);
        wrapper.set(BudgetInternalTransaction::getUpdateBy, LoginHelper.getUserId());
        wrapper.set(BudgetInternalTransaction::getUpdateTime, new Date());

        return transactionMapper.update(null, wrapper) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteById(Long id) {
        BudgetInternalTransaction existing = transactionMapper.selectById(id);
        if (existing == null) {
            throw new ServiceException("记录不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new ServiceException("只有草稿状态的记录才能删除");
        }
        return transactionMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return false;
        }
        for (Long id : ids) {
            BudgetInternalTransaction existing = transactionMapper.selectById(id);
            if (existing != null && !"DRAFT".equals(existing.getStatus())) {
                throw new ServiceException("记录[" + existing.getId() + "]已确认，无法删除");
            }
        }
        return transactionMapper.deleteByIds(ids) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean confirm(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return false;
        }
        LambdaUpdateWrapper<BudgetInternalTransaction> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(BudgetInternalTransaction::getId, ids);
        wrapper.eq(BudgetInternalTransaction::getStatus, "DRAFT");
        wrapper.set(BudgetInternalTransaction::getStatus, "CONFIRMED");
        wrapper.set(BudgetInternalTransaction::getUpdateBy, LoginHelper.getUserId());
        wrapper.set(BudgetInternalTransaction::getUpdateTime, new Date());
        return transactionMapper.update(null, wrapper) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean revokeConfirm(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return false;
        }
        LambdaUpdateWrapper<BudgetInternalTransaction> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(BudgetInternalTransaction::getId, ids);
        wrapper.eq(BudgetInternalTransaction::getStatus, "CONFIRMED");
        wrapper.set(BudgetInternalTransaction::getStatus, "DRAFT");
        wrapper.set(BudgetInternalTransaction::getUpdateBy, LoginHelper.getUserId());
        wrapper.set(BudgetInternalTransaction::getUpdateTime, new Date());
        return transactionMapper.update(null, wrapper) > 0;
    }

    @Override
    public List<Map<String, Object>> getEliminationConfigs(String transactionType) {
        LambdaQueryWrapper<BudgetEliminationConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StrUtil.isNotBlank(transactionType), BudgetEliminationConfig::getTransactionType, transactionType);
        wrapper.orderByAsc(BudgetEliminationConfig::getSortNum);
        List<BudgetEliminationConfig> configs = configMapper.selectList(wrapper);

        List<Map<String, Object>> result = new ArrayList<>();
        for (BudgetEliminationConfig config : configs) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("projectCode", config.getProjectCode());
            map.put("projectName", config.getProjectName());
            map.put("transactionType", config.getTransactionType());
            map.put("templateCode", config.getTemplateCode());
            map.put("matchKeyword", config.getMatchKeyword());
            result.add(map);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getDeptOptions() {
        // 查询所有部门，排除顶级节点（parent_id=0是国资委）
        LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(SysDept::getParentId, 0);
        wrapper.ne(SysDept::getStatus, "1"); // 排除停用
        wrapper.orderByAsc(SysDept::getAncestors);
        wrapper.orderByAsc(SysDept::getOrderNum);
        List<SysDept> depts = sysDeptMapper.selectList(wrapper);

        List<Map<String, Object>> result = new ArrayList<>();
        for (SysDept dept : depts) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("deptId", dept.getDeptId());
            map.put("deptName", dept.getDeptName());
            map.put("parentId", dept.getParentId());
            map.put("ancestors", dept.getAncestors());
            result.add(map);
        }
        return result;
    }
}
