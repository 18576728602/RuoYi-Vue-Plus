package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 提交版本快照对象 budget_data_version
 *
 * <p>PRD 7.2.1：每次提交审批时对当前填报数据生成不可变版本快照（V1/V2...），
 * 支撑两上两下「二上」阶段的版本对比与多轮修订。</p>
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_data_version")
public class BudgetDataVersion extends TenantEntity {

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
     * 填报单位(公司)ID
     */
    private Long deptId;

    /**
     * 预算表编号
     */
    private String templateCode;

    /**
     * 提交版本号(每方案+单位+表递增)
     */
    private Integer versionNo;

    /**
     * 科目编码
     */
    private String itemCode;

    /**
     * 科目名称
     */
    private String itemName;

    /**
     * 上年实际(万元)
     */
    private BigDecimal lastActual;

    /**
     * 本年预算(万元)
     */
    private BigDecimal budgetAmount;

    /**
     * 备注(01表信息内容等文本)
     */
    private String remark;

    /**
     * 提交人ID
     */
    private Long submittedBy;

    /**
     * 提交人名称
     */
    private String submittedName;

    /**
     * 提交时间
     */
    private Date submittedTime;

    /**
     * 快照类型 SUBMIT-提交快照 / RELEASE-正式发布快照（终审冻结）
     */
    private String releaseType;

    /**
     * 正式发布序号(每方案+单位+表递增,1=首个V1.0,2=V1.1...)
     */
    private Integer releaseSeq;

    /**
     * 正式版本号(如 V1.0 / V1.1)，仅 RELEASE 快照有值
     */
    private String releaseNo;
}