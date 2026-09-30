package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.budget.domain.bo.BudgetReportQuery;
import org.dromara.budget.domain.vo.BudgetReportVo;
import org.dromara.budget.service.IBudgetReportService;
import org.dromara.common.core.domain.R;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 预算执行分析报告（智能报告生成）
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/report")
public class BudgetReportController {

    private final IBudgetReportService budgetReportService;

    /**
     * 一键生成预算执行分析报告
     */
    @SaCheckPermission("budget:report:list")
    @GetMapping("/generate")
    public R<BudgetReportVo> generate(@Validated BudgetReportQuery q) {
        return R.ok(budgetReportService.generate(q));
    }

    /**
     * 导出预算执行分析报告为 Word(.doc)
     */
    @SaCheckPermission("budget:report:list")
    @PostMapping("/export")
    public void export(@Validated BudgetReportQuery q, HttpServletResponse response) throws IOException {
        String html = budgetReportService.generateWordHtml(q);
        String fileName = "预算执行分析报告_"
            + (q.getPeriod() == null || q.getPeriod().isEmpty() ? "自动" : q.getPeriod()) + ".doc";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/msword;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.addHeader("Content-Disposition", "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }
}