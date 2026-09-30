package org.dromara.budget.service;

import org.dromara.budget.domain.bo.BudgetReportQuery;
import org.dromara.budget.domain.vo.BudgetReportVo;

/**
 * 预算执行分析报告服务
 *
 * <p>一键生成"月度/季度预算执行分析报告"，结构参考成熟产品的智能报告：
 * 封面 → 执行概览(关键结论) → 按表成本费用分析 → 季度趋势 → 预警事项 →
 * 超预算科目 → 下月重点关注与建议。
 *
 * @author Lion Li
 * @date 2026-09-14
 */
public interface IBudgetReportService {

    /**
     * 生成预算执行分析报告
     */
    BudgetReportVo generate(BudgetReportQuery q);

    /**
     * 生成 Word(.doc) 兼容的 XHTML 报告正文
     */
    String generateWordHtml(BudgetReportQuery q);
}