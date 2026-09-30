package org.dromara.budget.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.domain.BudgetApprovalChain;
import org.dromara.budget.domain.BudgetApprovalChainNode;
import org.dromara.budget.domain.BudgetData;
import org.dromara.budget.mapper.BudgetApprovalChainMapper;
import org.dromara.budget.mapper.BudgetApprovalChainNodeMapper;
import org.dromara.budget.mapper.BudgetDataMapper;
import org.dromara.budget.mapper.BudgetPlanMapper;
import org.dromara.budget.util.BudgetUnitNameUtil;
import org.dromara.budget.service.IBudgetApprovalChainService;
import org.dromara.budget.service.IBudgetGatherMapService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.domain.SysDept;
import org.dromara.system.mapper.SysDeptMapper;
import org.dromara.system.mapper.SysMenuMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 预算多级审批链 Service实现
 *
 * <p>三级/四级孙公司填报时，从填报单位本级(sort_order=1)开始沿 ancestors 自下而上逐级审批，
 * 任一层驳回则整单打回 DRAFT，全部通过才置为 APPROVED（可正式发布）。</p>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BudgetApprovalChainServiceImpl implements IBudgetApprovalChainService {

    /** 填报(编制)审批权限标识 */
    private static final String APPROVE_PERM = "budget:approval:approve";

    /** 顶层岗位审批节点：财务部部长 */
    private static final String POST_PERM_MINISTER = "budget:approval:minister";
    /** 顶层岗位审批节点：集团财务总监（终审） */
    private static final String POST_PERM_DIRECTOR = "budget:approval:director";

    private final BudgetApprovalChainMapper chainMapper;
    private final BudgetApprovalChainNodeMapper chainNodeMapper;
    private final BudgetDataMapper budgetDataMapper;
    private final BudgetPlanMapper budgetPlanMapper;
    private final SysDeptMapper sysDeptMapper;
    private final SysMenuMapper sysMenuMapper;
    private final IBudgetGatherMapService budgetGatherMapService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BudgetApprovalChain createChain(Long planId, Long deptId, String templateCode, String submitRemark) {
        if (planId == null || deptId == null) {
            throw new ServiceException("方案与填报单位缺失，无法生成审批链");
        }
        SysDept dept = sysDeptMapper.selectById(deptId);
        if (dept == null) {
            throw new ServiceException("填报单位不存在，无法生成审批链");
        }

        // 解析祖先链（含自身）：ancestors = "0,100,101"，自身 dept 是链首
        List<Long> levels = buildLevels(dept);
        // 若填报单位本身无上级（已是公司总部/顶层），不生成链，走原有单级审批
        if (levels.size() <= 1) {
            return null;
        }

        // 过滤：只保留有审批人（拥有budget:approval:approve权限的用户）的部门
        List<Long> filteredLevels = filterLevelsWithApprover(levels);
        // 过滤后只剩本单位（没有上级有审批人），不生成链，走单级审批
        if (filteredLevels.size() <= 1) {
            return null;
        }

        // 取该单位该表下一条当前还未完结的链：有则复用（新一轮提交），否则新建一轮（按表分批审批：方案+单位+表为一链）
        BudgetApprovalChain active = findActiveChain(planId, deptId, templateCode);
        BudgetApprovalChain chain;
        int chainNo;
        if (active != null) {
            chain = active;
            chainNo = active.getChainNo();
        } else {
            chainNo = nextChainNo(planId, deptId, templateCode);
            chain = new BudgetApprovalChain();
            chain.setPlanId(planId);
            chain.setDeptId(deptId);
            chain.setTemplateCode(templateCode);
            chain.setChainNo(chainNo);
            chain.setStatus("PENDING");
            chain.setStartTime(new Date());
            chain.setApplicantId(LoginHelper.getUserId());
            chain.setApplicantName(LoginHelper.getUsername());
            chain.setRemark(submitRemark);
            chainMapper.insert(chain);
        }

        // 重建该轮节点：先逻辑删除旧节点避免残留（支持多轮提交复用链）
        chainNodeMapper.delete(new LambdaQueryWrapper<BudgetApprovalChainNode>()
            .eq(BudgetApprovalChainNode::getChainId, chain.getId()));

        int sort = 1;
        int total = filteredLevels.size();
        for (int idx = 0; idx < total; idx++) {
            Long nodeDeptId = filteredLevels.get(idx);
            if (nodeDeptId == null) {
                continue;
            }
            SysDept nd = sysDeptMapper.selectById(nodeDeptId);
            boolean isTop = (idx == total - 1);
            if (isTop) {
                // 顶层公司：拆成「财务部部长 → 集团财务总监」两个先后岗位节点
                String topName = (nd != null) ? nd.getDeptName() : String.valueOf(nodeDeptId);
                insertNode(chain, planId, deptId, chainNo, "POST",
                    nodeDeptId, topName + "-财务部部长", POST_PERM_MINISTER, sort++);
                insertNode(chain, planId, deptId, chainNo, "POST",
                    nodeDeptId, topName + "-集团财务总监", POST_PERM_DIRECTOR, sort++);
                continue;
            }
            insertNode(chain, planId, deptId, chainNo, "DEPT",
                nodeDeptId, (nd != null) ? nd.getDeptName() : String.valueOf(nodeDeptId), null, sort++);
        }
        return chain;
    }

    /**
     * 生成并插入一个审批节点（sort_order=1 为本级先审，其余排队）
     */
    private void insertNode(BudgetApprovalChain chain, Long planId, Long deptId, Integer chainNo, String nodeType,
                            Long nodeDeptId, String nodeDeptName, String postPerm, int sortOrder) {
        BudgetApprovalChainNode node = new BudgetApprovalChainNode();
        node.setChainId(chain.getId());
        node.setPlanId(planId);
        node.setDeptId(deptId);
        node.setTemplateCode(chain.getTemplateCode());
        node.setChainNo(chainNo);
        node.setNodeType(nodeType);
        node.setSortOrder(sortOrder);
        node.setNodeDeptId(nodeDeptId);
        node.setNodeDeptName(nodeDeptName);
        node.setPostPerm(postPerm);
        node.setLevelNo(sortOrder);
        node.setStatus(sortOrder == 1 ? "PENDING" : "WAITING");
        chainNodeMapper.insert(node);
    }

    @Override
    public Map<String, Object> getLatestChain(Long planId, Long deptId) {
        Map<String, Object> result = new HashMap<>();
        BudgetApprovalChain chain = findActiveChain(planId, deptId, null);
        if (chain == null) {
            // 无进行中链时取最近一条已完结链
            List<BudgetApprovalChain> done = chainMapper.selectList(
                new LambdaQueryWrapper<BudgetApprovalChain>()
                    .eq(BudgetApprovalChain::getPlanId, planId)
                    .eq(BudgetApprovalChain::getDeptId, deptId)
                    .orderByDesc(BudgetApprovalChain::getChainNo)
                    .last("LIMIT 1"));
            if (CollUtil.isEmpty(done)) {
                result.put("chain", null);
                result.put("nodes", new ArrayList<>());
                return result;
            }
            chain = done.get(0);
        }
        result.put("chain", wrapChain(chain, true));
        result.put("nodes", listNodes(chain.getId()));
        return result;
    }

    @Override
    public List<Map<String, Object>> listMyPendingChains() {
        Long currentDeptId = LoginHelper.getDeptId();
        List<Map<String, Object>> result = new ArrayList<>();
        if (currentDeptId == null) {
            return result;
        }
        // 当前用户所属部门及其祖先（决定他可审批的层级范围）
        SysDept curDept = sysDeptMapper.selectById(currentDeptId);
        if (curDept == null) {
            return result;
        }
        List<Long> myDeptScope = buildLevels(curDept);

        // 所有 PENDING 节点（跨所有链）
        List<BudgetApprovalChainNode> pendingNodes = chainNodeMapper.selectList(
            new LambdaQueryWrapper<BudgetApprovalChainNode>()
                .eq(BudgetApprovalChainNode::getStatus, "PENDING"));

        // 是否拥有审批权限
        boolean hasPerm = hasApprovePerm(LoginHelper.getUserId());

        Map<Long, BudgetApprovalChain> chainMap = new HashMap<>();
        if (!pendingNodes.isEmpty()) {
            List<Long> chainIds = pendingNodes.stream()
                .map(BudgetApprovalChainNode::getChainId).distinct().collect(Collectors.toList());
            for (BudgetApprovalChain c : chainMapper.selectBatchIds(chainIds)) {
                chainMap.put(c.getId(), c);
            }
        }

        Map<Long, String> deptNameMap = loadDeptNameMap();
        Map<Long, String> planNameMap = loadPlanNameMap();

        for (BudgetApprovalChainNode node : pendingNodes) {
            BudgetApprovalChain chain = chainMap.get(node.getChainId());
            if (chain == null) {
                continue;
            }
            // 该节点审批层级：岗位节点按岗位权限识别本人，公司节点按层级范围识别
            boolean mineLevel;
            if ("POST".equals(node.getNodeType())) {
                mineLevel = hasPostPerm(LoginHelper.getUserId(), node.getPostPerm());
            } else {
                mineLevel = myDeptScope.contains(node.getNodeDeptId());
            }
            if (!mineLevel) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("chainId", chain.getId());
            item.put("chainNo", chain.getChainNo());
            item.put("nodeId", node.getId());
            item.put("planId", chain.getPlanId());
            item.put("planName", planNameMap.getOrDefault(chain.getPlanId(), ""));
            item.put("deptId", chain.getDeptId());
            item.put("deptName", deptNameMap.getOrDefault(chain.getDeptId(), String.valueOf(chain.getDeptId())));
            item.put("templateCode", chain.getTemplateCode());
            item.put("nodeDeptId", node.getNodeDeptId());
            item.put("nodeDeptName", node.getNodeDeptName());
            item.put("nodeType", node.getNodeType());
            item.put("sortOrder", node.getSortOrder());
            item.put("levelNo", node.getLevelNo());
            item.put("applicantName", chain.getApplicantName());
            item.put("startTime", chain.getStartTime());
            item.put("submitRemark", chain.getRemark());
            item.put("hasPerm", "POST".equals(node.getNodeType())
                ? hasPostPerm(LoginHelper.getUserId(), node.getPostPerm())
                : hasPerm);
            item.put("type", "FILL");
            item.put("typeLabel", "预算填报");
            item.put("status", "PENDING");
            result.add(item);
        }
        return result;
    }

    @Override
    public List<BudgetApprovalChain> listPendingChainsForDept(Long planId, Long nodeDeptId) {
        // 该方法供全局账号在审批中心按公司筛选待办使用
        if (nodeDeptId == null) {
            return new ArrayList<>();
        }
        List<BudgetApprovalChainNode> nodes = chainNodeMapper.selectList(
            new LambdaQueryWrapper<BudgetApprovalChainNode>()
                .eq(BudgetApprovalChainNode::getNodeDeptId, nodeDeptId)
                .eq(BudgetApprovalChainNode::getStatus, "PENDING"));
        if (nodes.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> chainIds = nodes.stream().map(BudgetApprovalChainNode::getChainId).distinct()
            .collect(Collectors.toList());
        return chainMapper.selectBatchIds(chainIds).stream()
            .filter(c -> planId == null || Objects.equals(c.getPlanId(), planId))
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String advanceChain(Long chainNodeId, boolean approve, String comment) {
        if (chainNodeId == null) {
            throw new ServiceException("审批节点缺失");
        }
        BudgetApprovalChainNode node = chainNodeMapper.selectById(chainNodeId);
        if (node == null) {
            throw new ServiceException("审批节点不存在");
        }
        // 权限校验：公司节点须有审批权限；岗位节点可有岗位权限
        Long userId = LoginHelper.getUserId();
        boolean hasPost = hasPostPerm(userId, node.getPostPerm());
        if (!hasApprovePerm(userId) && !hasPost) {
            throw new ServiceException("当前用户无预算填报审批权限");
        }
        SysDept curDept = sysDeptMapper.selectById(LoginHelper.getDeptId());
        boolean allowed;
        if ("POST".equals(node.getNodeType())) {
            allowed = hasPost;
        } else {
            allowed = curDept != null && buildLevels(curDept).contains(node.getNodeDeptId());
        }
        if (!allowed) {
            throw new ServiceException("当前用户不属于该节点的审批公司层级，无法审批");
        }
        if (!"PENDING".equals(node.getStatus())) {
            throw new ServiceException("该节点已被处理或尚未轮到审批");
        }

        BudgetApprovalChain chain = chainMapper.selectById(node.getChainId());
        if (chain == null) {
            throw new ServiceException("审批链不存在");
        }

        // 记录当前节点处理结果
        LambdaUpdateWrapper<BudgetApprovalChainNode> uwNode = new LambdaUpdateWrapper<>();
        uwNode.eq(BudgetApprovalChainNode::getId, node.getId());
        uwNode.set(BudgetApprovalChainNode::getStatus, approve ? "APPROVED" : "REJECTED");
        uwNode.set(BudgetApprovalChainNode::getApproverId, userId);
        uwNode.set(BudgetApprovalChainNode::getApproverName, LoginHelper.getUsername());
        uwNode.set(BudgetApprovalChainNode::getApproveTime, new Date());
        uwNode.set(BudgetApprovalChainNode::getComment, comment);
        chainNodeMapper.update(null, uwNode);

        if (!approve) {
            // 任一层驳回 → 整链驳回 + 业务数据打回草稿
            LambdaUpdateWrapper<BudgetApprovalChain> uwChain = new LambdaUpdateWrapper<>();
            uwChain.eq(BudgetApprovalChain::getId, chain.getId());
            uwChain.set(BudgetApprovalChain::getStatus, "REJECTED");
            chainMapper.update(null, uwChain);
            rejectBudgetData(chain.getPlanId(), chain.getDeptId(), chain.getTemplateCode(), comment);
            return "REJECTED";
        }

        // 通过：激活下一个排队节点
        List<BudgetApprovalChainNode> siblings = listNodes(chain.getId());
        int curSort = node.getSortOrder();
        // 找到第一个尚未处理且顺序靠后的节点
        BudgetApprovalChainNode next = siblings.stream()
            .filter(n -> n.getSortOrder() > curSort)
            .filter(n -> "WAITING".equals(n.getStatus()))
            .min(java.util.Comparator.comparingInt(BudgetApprovalChainNode::getSortOrder))
            .orElse(null);
        if (next == null) {
            // 全部通过 → 整链完结，业务数据置为 APPROVED
            LambdaUpdateWrapper<BudgetApprovalChain> uwChain = new LambdaUpdateWrapper<>();
            uwChain.eq(BudgetApprovalChain::getId, chain.getId());
            uwChain.set(BudgetApprovalChain::getStatus, "APPROVED");
            chainMapper.update(null, uwChain);
            approveBudgetData(chain.getPlanId(), chain.getDeptId(), chain.getTemplateCode());
            return "APPROVED";
        }
        LambdaUpdateWrapper<BudgetApprovalChainNode> uwNext = new LambdaUpdateWrapper<>();
        uwNext.eq(BudgetApprovalChainNode::getId, next.getId());
        uwNext.set(BudgetApprovalChainNode::getStatus, "PENDING");
        chainNodeMapper.update(null, uwNext);
        return "PENDING";
    }

    /**
     * 解析某部门从自身到集团的层级（自下而上，首元素为自身）
     * 审批链最多到集团层级，不到国资委等政府监管机构（parent_id=0的部门）
     */
    private List<Long> buildLevels(SysDept dept) {
        List<Long> levels = new ArrayList<>();
        if (dept.getDeptId() != null) {
            levels.add(dept.getDeptId());
        }
        if (StrUtil.isNotBlank(dept.getAncestors())) {
            String[] parts = dept.getAncestors().split(",");
            // 反向遍历，剔除 "0"，从最近的祖先开始向上
            for (int i = parts.length - 1; i >= 0; i--) {
                try {
                    long id = Long.parseLong(parts[i].trim());
                    if (id != 0L && !levels.contains(id)) {
                        // 排除国资委等政府监管机构（parent_id=0的顶层部门）
                        SysDept ancestor = sysDeptMapper.selectById(id);
                        if (ancestor != null && ancestor.getParentId() != null && ancestor.getParentId() == 0L) {
                            continue;
                        }
                        levels.add(id);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return levels;
    }

    /**
     * 过滤部门列表，只保留有审批人（拥有budget:approval:approve权限的用户）的部门
     */
    private List<Long> filterLevelsWithApprover(List<Long> levels) {
        List<Long> result = new ArrayList<>();
        for (Long deptId : levels) {
            if (deptId == null) continue;
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT u.user_id) FROM sys_user u " +
                "JOIN sys_user_role ur ON u.user_id = ur.user_id " +
                "JOIN sys_role_menu rm ON ur.role_id = rm.role_id " +
                "JOIN sys_menu m ON rm.menu_id = m.menu_id " +
                "WHERE m.perms = 'budget:approval:approve' AND u.status = '0' AND u.del_flag = '0' " +
                "AND u.dept_id = ?",
                Integer.class, deptId);
            if (cnt != null && cnt > 0) {
                result.add(deptId);
            }
        }
        return result;
    }

    private BudgetApprovalChain findActiveChain(Long planId, Long deptId, String templateCode) {
        List<BudgetApprovalChain> list = chainMapper.selectList(
            new LambdaQueryWrapper<BudgetApprovalChain>()
                .eq(BudgetApprovalChain::getPlanId, planId)
                .eq(BudgetApprovalChain::getDeptId, deptId)
                .eq(StrUtil.isNotBlank(templateCode), BudgetApprovalChain::getTemplateCode, templateCode)
                .eq(BudgetApprovalChain::getStatus, "PENDING")
                .orderByDesc(BudgetApprovalChain::getChainNo)
                .last("LIMIT 1"));
        return CollUtil.isEmpty(list) ? null : list.get(0);
    }

    private int nextChainNo(Long planId, Long deptId, String templateCode) {
        List<BudgetApprovalChain> list = chainMapper.selectList(
            new LambdaQueryWrapper<BudgetApprovalChain>()
                .eq(BudgetApprovalChain::getPlanId, planId)
                .eq(BudgetApprovalChain::getDeptId, deptId)
                .eq(StrUtil.isNotBlank(templateCode), BudgetApprovalChain::getTemplateCode, templateCode)
                .orderByDesc(BudgetApprovalChain::getChainNo)
                .last("LIMIT 1"));
        return (list.isEmpty() || list.get(0).getChainNo() == null) ? 1 : list.get(0).getChainNo() + 1;
    }

    private List<BudgetApprovalChainNode> listNodes(Long chainId) {
        return chainNodeMapper.selectList(
            new LambdaQueryWrapper<BudgetApprovalChainNode>()
                .eq(BudgetApprovalChainNode::getChainId, chainId)
                .orderByAsc(BudgetApprovalChainNode::getSortOrder));
    }

    private Map<String, Object> wrapChain(BudgetApprovalChain chain, boolean includeDetail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", chain.getId());
        m.put("planId", chain.getPlanId());
        m.put("deptId", chain.getDeptId());
        m.put("templateCode", chain.getTemplateCode());
        m.put("chainNo", chain.getChainNo());
        m.put("status", chain.getStatus());
        m.put("startTime", chain.getStartTime());
        m.put("applicantName", chain.getApplicantName());
        m.put("remark", chain.getRemark());
        return m;
    }

    /**
     * 当前用户是否拥有预算填报审批权限（含超管/租户管理）
     */
    private boolean hasApprovePerm(Long userId) {
        if (userId == null) {
            return false;
        }
        if (LoginHelper.isSuperAdmin() || LoginHelper.isTenantAdmin()) {
            return true;
        }
        java.util.Set<String> perms = sysMenuMapper.selectMenuPermsByUserId(userId);
        return perms != null && perms.contains(APPROVE_PERM);
    }

    /**
     * 当前用户是否拥有某岗位节点的审批权限（含超管/租户管理）
     */
    private boolean hasPostPerm(Long userId, String postPerm) {
        if (userId == null || StrUtil.isBlank(postPerm)) {
            return false;
        }
        if (LoginHelper.isSuperAdmin() || LoginHelper.isTenantAdmin()) {
            return true;
        }
        java.util.Set<String> perms = sysMenuMapper.selectMenuPermsByUserId(userId);
        return perms != null && perms.contains(postPerm);
    }

    /**
     * 该表整链通过时，将该表填报数据置为 APPROVED（可正式发布），并触发正式归集
     * （正式预算金额=审批通过口径：通过的表进正式，未通过的表保持 SUBMITTED/DRAFT 归填报）
     */
    private void approveBudgetData(Long planId, Long deptId, String templateCode) {
        LambdaUpdateWrapper<BudgetData> uw = new LambdaUpdateWrapper<>();
        uw.eq(BudgetData::getPlanId, planId)
            .eq(BudgetData::getDeptId, deptId)
            .eq(StrUtil.isNotBlank(templateCode), BudgetData::getTemplateCode, templateCode)
            .eq(BudgetData::getStatus, "SUBMITTED")
            .set(BudgetData::getStatus, "APPROVED")
            .set(BudgetData::getRemark, null);
        budgetDataMapper.update(null, uw);

        // 审批完全通过后归集：把该方案下各部门 APPROVED 金额累加到目标部门目标科目。
        // afterCommit：归集失败不影响审批，且使用提交后的最新正式数据。
        final Long fPlanId = planId;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    gatherAfterApproval(fPlanId);
                }
            });
        } else {
            gatherAfterApproval(fPlanId);
        }
    }

    /**
     * 审批通过后的自动归集：失败仅记日志，不回滚审批、不影响主流程
     */
    private void gatherAfterApproval(Long planId) {
        try {
            budgetGatherMapService.gather(planId);
        } catch (Exception e) {
            log.error("审批通过后自动归集失败, planId={}", planId, e);
        }
    }

    /**
     * 该表任一层驳回时，将该表填报数据打回草稿并写入驳回原因
     */
    private void rejectBudgetData(Long planId, Long deptId, String templateCode, String comment) {
        LambdaQueryWrapper<BudgetData> qw = new LambdaQueryWrapper<>();
        qw.eq(BudgetData::getPlanId, planId)
            .eq(BudgetData::getDeptId, deptId)
            .eq(StrUtil.isNotBlank(templateCode), BudgetData::getTemplateCode, templateCode)
            .in(BudgetData::getStatus, "SUBMITTED");
        List<BudgetData> list = budgetDataMapper.selectList(qw);
        for (BudgetData d : list) {
            LambdaUpdateWrapper<BudgetData> uw = new LambdaUpdateWrapper<>();
            uw.eq(BudgetData::getId, d.getId())
                .set(BudgetData::getStatus, "DRAFT")
                .set(BudgetData::getRemark, "【驳回-" + (LoginHelper.getUsername() == null ? "" : LoginHelper.getUsername()) + "】"
                    + (StrUtil.isBlank(comment) ? "未通过审批" : comment));
            budgetDataMapper.update(null, uw);
        }
    }

    private Map<Long, String> loadDeptNameMap() {
        Map<Long, String> map = new HashMap<>();
        for (SysDept d : sysDeptMapper.selectList(null)) {
            map.putIfAbsent(d.getDeptId(), d.getDeptName());
        }
        return map;
    }

    private Map<Long, String> loadPlanNameMap() {
        Map<Long, String> map = new HashMap<>();
        budgetPlanMapper.selectList(null).forEach(p -> map.putIfAbsent(p.getId(), p.getPlanName()));
        return map;
    }

    /** 兼容已有装饰工具（同包引用），避免循环依赖 */
    private String unitDisplayName(Long deptId) {
        SysDept d = deptId == null ? null : sysDeptMapper.selectById(deptId);
        if (d == null) {
            return String.valueOf(deptId);
        }
        SysDept parent = d.getParentId() == null ? null : sysDeptMapper.selectById(d.getParentId());
        return BudgetUnitNameUtil.format(d.getDeptName(), parent != null ? parent.getDeptName() : null);
    }
}