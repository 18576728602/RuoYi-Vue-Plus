package org.dromara.budget.service;

import java.util.List;
import java.util.Map;

/**
 * 提交版本快照 Service
 *
 * <p>PRD 7.2.1 / 两上两下：每次提交审批生成不可变版本快照，支撑版本对比与多轮修订。</p>
 */
public interface IBudgetDataVersionService {

    /**
     * 对某方案+单位(+模板)的当前 SUBMITTED 数据生成一个不可变版本快照
     *
     * @return 本次生成的版本号
     */
    int snapshot(Long planId, Long deptId, String templateCode);

    /**
     * 查询某方案+单位(+模板)的版本历史摘要（按版本号分组合并）
     */
    List<Map<String, Object>> listVersions(Long planId, Long deptId, String templateCode);

    /**
     * 对比某方案+单位(+模板)的两个版本 v1 / v2，返回逐科目差异明细
     */
    Map<String, Object> compareVersions(Long planId, Long deptId, String templateCode, int v1, int v2);

    /**
     * 恢复历史版本：将指定版本快照的数据回写到当前填报数据（仅限草稿/未提交状态）
     */
    int restoreVersion(Long planId, Long deptId, String templateCode, int versionNo);

    /**
     * 计算该范围下一次正式发布序号（首个为 1 → V1.0）
     */
    int nextReleaseSeq(Long planId, Long deptId, String templateCode);

    /**
     * 将该范围当前 APPROVED 数据生成为「正式发布(RELEASE)」快照（终审冻结 V1.0/V1.1）
     */
    int releaseSnapshot(Long planId, Long deptId, String templateCode, String releaseNo, int seq);

    /**
     * 查询某个版本的快照明细（首页动态点击查看）
     */
    List<Map<String, Object>> versionDetail(Long planId, Long deptId, String templateCode, int versionNo);
}