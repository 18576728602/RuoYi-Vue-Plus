package org.dromara.budget.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.budget.domain.vo.BudgetTemplateItemVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.Collection;

/**
 * 预算模板科目Mapper接口
 *
 * @author Lion Li
 * @date 2026-08-29
 */
public interface BudgetTemplateItemMapper extends BaseMapperPlus<BudgetTemplateItem, BudgetTemplateItemVo> {

    /**
     * 分页查询某方案的已停用科目（del_flag=1，绕过逻辑删除过滤，用于"还原"管理）
     *
     * @param page         分页参数
     * @param planId       方案ID
     * @param templateCode 预算表编号(null 查全部)
     * @return 已停用科目分页
     */
    IPage<BudgetTemplateItemVo> selectDisabledPage(IPage<BudgetTemplateItemVo> page,
                                                   @Param("planId") Long planId,
                                                   @Param("templateCode") String templateCode);

    /**
     * 还原已停用科目（del_flag 1 -> 0）
     *
     * @param ids 科目主键集合
     * @return 还原影响行数
     */
    int restoreByIds(@Param("ids") Collection<Long> ids);

    /**
     * 级联更新挂接快照中所有后代的编码前缀：
     * item_code 以 oldCode 开头的行，把前缀替换为 newCode；
     * 同时同步更新 parent_code。
     * 用于主数据科目编码变更时，同步刷新所有挂接快照的编码。
     *
     * @param oldCode 旧编码前缀
     * @param newCode 新编码前缀
     * @return 更新行数
     */
    @Update("UPDATE budget_template_item " +
        "SET item_code = CONCAT(#{newCode}, SUBSTRING(item_code, LENGTH(#{oldCode}) + 1)), " +
        "    parent_code = CASE " +
        "        WHEN parent_code = #{oldCode} THEN #{newCode} " +
        "        WHEN parent_code LIKE CONCAT(#{oldCode}, '%') THEN CONCAT(#{newCode}, SUBSTRING(parent_code, LENGTH(#{oldCode}) + 1)) " +
        "        ELSE parent_code " +
        "    END " +
        "WHERE del_flag = 0 " +
        "  AND item_code LIKE CONCAT(#{oldCode}, '%')")
    int cascadeUpdateDescendantCode(@Param("oldCode") String oldCode,
                                     @Param("newCode") String newCode);
}
