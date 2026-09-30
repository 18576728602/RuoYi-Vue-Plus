package org.dromara.budget.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 智能预警闭环处理记录 budget_warning_record
 *
 * <p>记录预警触达、确认、整改、跟踪、升级、解除的完整闭环，用于预警责任追踪与留痕审计。
 *
 * @author Lion Li
 * @date 2026-09-15
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("budget_warning_record")
public class BudgetWarningRecord extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "record_id")
    private Long recordId;

    /**
     * 预算方案ID
     */
    private Long planId;

    /**
     * 预警单位ID
     */
    private Long orgId;

    /**
     * 预算表编号
     */
    private String templateCode;

    /**
     * 科目编码
     */
    private String itemCode;

    /**
     * 预警等级: RED=超预算, ORANGE=进度超前, YELLOW=临近阈值
     */
    private String level;

    /**
     * 多源预警标识(逗号分隔): EXEC=执行, ADJUST=预算调整, SENSITIVE=敏感支出
     */
    private String sources;

    /**
     * 综合风险评分(0-100)
     */
    private BigDecimal riskScore;

    /**
     * 执行率(%)
     */
    private BigDecimal execRate;

    /**
     * 状态: OPEN=待处置, PROCESSING=处置中, CONFIRMED=已确认, RESOLVED=已整改, ESCALATED=已升级, CLOSED=已解除
     */
    private String status;

    /**
     * 处置人ID
     */
    private Long handlerId;

    /**
     * 处置人姓名
     */
    private String handlerName;

    /**
     * 处置/整改措施
     */
    private String improvePlan;

    /**
     * 跟踪结果
     */
    private String trackResult;

    /**
     * 是否已升级: 0=否, 1=是
     */
    private Integer escalated;

    /**
     * 升级对象(如 分管副总)
     */
    private String escalateTo;

    /**
     * 处置时间
     */
    private Date handleTime;

    /**
     * 解除时间
     */
    private Date closeTime;

    /**
     * 备注
     */
    private String remark;

    /**
     * 乐观锁版本号
     */
    @Version
    private Long version;

    /**
     * 删除标志: 0=正常, 其他=已删除
     */
    @TableLogic
    private Long delFlag;
}