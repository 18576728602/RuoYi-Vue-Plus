package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.dromara.budget.service.IBudgetDataVersionService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 提交版本快照（PRD 7.2.1 / 两上两下）
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/dataVersion")
public class BudgetDataVersionController {

    private final IBudgetDataVersionService budgetDataVersionService;

    /**
     * 版本历史摘要
     */
    @SaCheckPermission("budget:fill:list")
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list(@RequestParam(required = false) Long planId,
                                             @RequestParam(required = false) Long deptId,
                                             @RequestParam(required = false) String templateCode) {
        return R.ok(budgetDataVersionService.listVersions(planId, deptId, templateCode));
    }

    /**
     * 两个提交版本对比（V1 vs V2）
     */
    @SaCheckPermission("budget:fill:list")
    @GetMapping("/compare")
    public R<Map<String, Object>> compare(@NotNull(message = "方案不能为空") Long planId,
                                          @NotNull(message = "填报单位不能为空") Long deptId,
                                          @RequestParam(required = false) String templateCode,
                                          @NotNull(message = "版本1不能为空") Integer v1,
                                          @NotNull(message = "版本2不能为空") Integer v2) {
        return R.ok(budgetDataVersionService.compareVersions(planId, deptId, templateCode, v1, v2));
    }

    /**
     * 恢复历史版本：将指定版本快照回写到当前填报数据（仅限草稿）
     */
    @SaCheckPermission("budget:fill:edit")
    @org.dromara.common.log.annotation.Log(title = "预算版本恢复", businessType = org.dromara.common.log.enums.BusinessType.UPDATE)
    @org.dromara.common.idempotent.annotation.RepeatSubmit()
    @PostMapping("/restore")
    public R<Integer> restore(@RequestBody Map<String, Object> body) {
        Long planId = body.get("planId") == null ? null : Long.valueOf(String.valueOf(body.get("planId")));
        Long deptId = body.get("deptId") == null ? null : Long.valueOf(String.valueOf(body.get("deptId")));
        String templateCode = body.get("templateCode") == null ? null : String.valueOf(body.get("templateCode"));
        Integer versionNo = body.get("versionNo") == null ? null : Integer.valueOf(String.valueOf(body.get("versionNo")));
        return R.ok(budgetDataVersionService.restoreVersion(planId, deptId, templateCode, versionNo));
    }

    /**
     * 查询某个版本的快照明细（首页动态点击查看）
     */
    @SaCheckPermission(value = {"budget:fill:list", "budget:approval:list", "budget:dashboard:view"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    @GetMapping("/versionDetail")
    public R<List<Map<String, Object>>> versionDetail(@RequestParam Long planId,
                                                       @RequestParam Long deptId,
                                                       @RequestParam(required = false) String templateCode,
                                                       @RequestParam Integer versionNo) {
        return R.ok(budgetDataVersionService.versionDetail(planId, deptId, templateCode, versionNo));
    }
}