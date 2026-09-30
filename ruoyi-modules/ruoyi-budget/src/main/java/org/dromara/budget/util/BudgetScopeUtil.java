package org.dromara.budget.util;

import cn.hutool.core.util.StrUtil;
import org.dromara.system.domain.SysDept;
import org.dromara.system.mapper.SysDeptMapper;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 科目"适用公司"范围工具。
 * 规则：orgScope 为空/Null = 适用于全部公司；非空 = 逗号分隔的 dept_id 白名单。
 * <p>升级：命中自身或其任一上级公司(祖先,取自 sys_dept.ancestors) 即视为适用，
 * 使作用于某集团/二级单位的科目能天然向下覆盖其三四级子公司。</p>
 */
@Component
public class BudgetScopeUtil {

    private static SysDeptMapper sysDeptMapper;

    public BudgetScopeUtil(SysDeptMapper sysDeptMapper) {
        BudgetScopeUtil.sysDeptMapper = sysDeptMapper;
    }

    private BudgetScopeUtil() {
    }

    /**
     * 判断某科目是否适用于指定公司(自身或其任一祖先命中即适用)。
     *
     * @param orgScope 科目的适用公司范围（逗号分隔的 dept_id，空=全部）
     * @param deptId   公司ID
     */
    public static boolean appliesTo(String orgScope, Long deptId) {
        if (StrUtil.isBlank(orgScope) || deptId == null) {
            return true;
        }
        Set<Long> ids = Arrays.stream(orgScope.split(","))
            .map(String::trim)
            .filter(StrUtil::isNotBlank)
            .map(Long::valueOf)
            .collect(Collectors.toSet());
        if (ids.contains(deptId)) {
            return true;
        }
        // 祖先继承: dept 的任一上级在 orgScope 内即适用
        if (sysDeptMapper != null) {
            SysDept dept = sysDeptMapper.selectById(deptId);
            if (dept != null && StrUtil.isNotBlank(dept.getAncestors())) {
                for (String s : dept.getAncestors().split(",")) {
                    if (StrUtil.isBlank(s) || "0".equals(s.trim())) {
                        continue;
                    }
                    try {
                        if (ids.contains(Long.valueOf(s.trim()))) {
                            return true;
                        }
                    } catch (NumberFormatException ignore) {
                        // 忽略非数字祖先段
                    }
                }
            }
        }
        return false;
    }
}