package org.dromara.budget.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 预算执行分析报告
 *
 * <p>聚合 执行概览 / 按表成本费用分析 / 季度趋势 / 预警事项 /
 * 超预算科目 / 重点关注与建议，输出管理层可直接阅读的报告。数据全部来自已审批执行数据。
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
public class BudgetReportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * ---- 封面 ----
     */
    private String reportName;

    /**
     * 报告期间
     */
    private String reportPeriod;

    /**
     * 生成时间
     */
    private Date generateTime;

    /**
     * 预算方案名
     */
    private String planName;

    /**
     * 单位名(空=集团全口径)
     */
    private String orgName;

    /**
     * 单位范围：集团/单位名
     */
    private String scopeLabel;

    /**
     * ---- 执行概览 ----
     */
    private BigDecimal budgetTotal;

    private BigDecimal execTotal;

    private BigDecimal diffTotal;

    private BigDecimal diffRate;

    private BigDecimal execRate;

    private Long orgCount;

    private Long itemCount;

    private Long warnTotal;

    private Long warnRed;

    private Long warnOrange;

    private Long warnYellow;

    /**
     * 关键结论(文本列表)
     */
    private List<String> conclusions;

    /**
     * ---- 按表成本费用分析 ----
     */
    private List<Map<String, Object>> tableAnalysis;

    /**
     * ---- 季度执行趋势 ----
     */
    private List<Map<String, Object>> quarterTrend;

    /**
     * ---- 预警事项 ----
     */
    private List<Map<String, Object>> warningList;

    /**
     * ---- 超预算科目(差异归因Top) ----
     */
    private List<Map<String, Object>> overrunSubjects;

    /**
     * ---- 下月重点关注 ----
     */
    private List<String> focusPoints;

    /**
     * ---- 管理建议 ----
     */
    private List<String> suggestions;
}