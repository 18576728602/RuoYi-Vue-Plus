package org.dromara.budget.service;

/**
 * 终审·正式发布 Service（PRD 7.1 终审 / 数据冻结 / 正式版本 V1.0/V1.1）
 *
 * <p>在集团对某方案+单位+预算表二上数据全部审批通过后，执行正式发布：
 * 冻结数据（APPROVED→PUBLISHED）并生成不可变 RELEASE 快照，形成正式版本 V1.0，
 * 后续调整通过后再次发布生成 V1.1 调整版。</p>
 */
public interface IBudgetReleaseService {

    /**
     * 正式发布并冻结：校验整个范围均为已审批通过，生成正式版本快照并置为 PUBLISHED
     *
     * @return 生成的正式版本号（如 V1.0 / V1.1）
     */
    String publish(Long planId, Long deptId, String templateCode);

    /**
     * 查询某方案+单位(+模板)是否已存在正式发布版本
     */
    Integer currentReleaseSeq(Long planId, Long deptId, String templateCode);
}