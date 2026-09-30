package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 预算目标下达对象 budget_target（一下）
 *
 * <p>PRD 7.1.2 第一阶段：集团预算管理员按 方案+公司+板块(科目) 下达预算目标基准线，
 * 设置弹性区间（如±5%），状态 草稿→已下达，每次目标调整生成版本。</p>
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_target")
public class BudgetTarget extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 预算方案ID
     */
    private Long planId;

    /**
     * 预算表编号
     */
    private String templateCode;

    /**
     * 目标公司ID
     */
    private Long deptId;

    /**
     * 科目编码
     */
    private String itemCode;

    /**
     * 科目名称
     */
    private String itemName;

    /**
     * 目标金额(万元)
     */
    private BigDecimal targetAmount;

    /**
     * 下限偏差率(-0.05表示-5%)
     */
    private BigDecimal toleranceMin;

    /**
     * 上限偏差率(0.05表示+5%)
     */
    private BigDecimal toleranceMax;

    /**
     * 科目排序
     */
    private Integer itemOrder;

    /**
     * 目标版本号
     */
    private Integer versionNo;

    /**
     * DRAFT草稿/PUBLISHED已下达
     */
    private String status;
}