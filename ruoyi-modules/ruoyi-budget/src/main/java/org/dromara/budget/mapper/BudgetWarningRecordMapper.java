package org.dromara.budget.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.budget.domain.BudgetWarningRecord;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 智能预警闭环处理记录 Mapper
 *
 * @author Lion Li
 * @date 2026-09-15
 */
@Mapper
public interface BudgetWarningRecordMapper extends BaseMapperPlus<BudgetWarningRecord, BudgetWarningRecord> {

}