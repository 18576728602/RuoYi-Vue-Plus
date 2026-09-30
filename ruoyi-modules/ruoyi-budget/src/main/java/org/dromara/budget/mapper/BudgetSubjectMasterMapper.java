package org.dromara.budget.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.dromara.budget.domain.BudgetSubjectMaster;
import org.dromara.budget.domain.vo.BudgetSubjectMasterVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 预算科目主数据Mapper接口
 *
 * @author Lion Li
 * @date 2026-09-15
 */
public interface BudgetSubjectMasterMapper extends BaseMapperPlus<BudgetSubjectMaster, BudgetSubjectMasterVo> {

    /**
     * 级联更新所有后代科目的编码前缀：
     * subject_code 以 oldCode 开头的行，把前缀替换为 newCode；
     * 同时同步更新 parent_code（以 oldCode 开头的 parent_code 也替换前缀）。
     * 用于父级科目编码变更时，一次性更新所有子子孙孙的编码和父级编码。
     *
     * @param oldCode 旧编码前缀
     * @param newCode 新编码前缀
     * @param subjectType 科目类型（SYS/DEPT），用于缩小范围
     * @param orgId 归属公司ID，用于缩小范围（可为null）
     * @return 更新行数
     */
    @Update("UPDATE budget_subject_master " +
        "SET subject_code = CONCAT(#{newCode}, SUBSTRING(subject_code, LENGTH(#{oldCode}) + 1)), " +
        "    parent_code = CASE " +
        "        WHEN parent_code = #{oldCode} THEN #{newCode} " +
        "        WHEN parent_code LIKE CONCAT(#{oldCode}, '%') THEN CONCAT(#{newCode}, SUBSTRING(parent_code, LENGTH(#{oldCode}) + 1)) " +
        "        ELSE parent_code " +
        "    END, " +
        "    version = version + 1 " +
        "WHERE del_flag = 0 " +
        "  AND subject_code LIKE CONCAT(#{oldCode}, '%') " +
        "  AND subject_code != #{oldCode} " +
        "  AND subject_type = #{subjectType} " +
        "  AND (org_id = #{orgId} OR (#{orgId} IS NULL AND org_id IS NULL))")
    int cascadeUpdateDescendantCode(@Param("oldCode") String oldCode,
                                     @Param("newCode") String newCode,
                                     @Param("subjectType") String subjectType,
                                     @Param("orgId") Long orgId);
}
