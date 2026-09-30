package org.dromara.budget.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.budget.service.IBudgetReleaseService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 终审·正式发布（PRD 7.1 终审：数据冻结与正式版本 V1.0/V1.1）
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/release")
public class BudgetReleaseController {

    private final IBudgetReleaseService releaseService;

    /**
     * 正式发布并冻结：方案+单位(+预算表)数据必须全部已终审通过
     */
    @PostMapping("/publish")
    public R<String> publish(@RequestBody Map<String, Object> body) {
        Long planId = toLong(body.get("planId"));
        Long deptId = toLong(body.get("deptId"));
        String templateCode = body.get("templateCode") == null ? null : String.valueOf(body.get("templateCode"));
        return R.ok(releaseService.publish(planId, deptId, templateCode));
    }

    /**
     * 查询当前已发布次数（0=未发布）
     */
    @SaCheckPermission(value = {"budget:approval:list", "budget:fill:list"}, mode = SaMode.OR)
    @GetMapping("/seq")
    public R<Integer> seq(@RequestParam Long planId,
                          @RequestParam Long deptId,
                          @RequestParam(required = false) String templateCode) {
        return R.ok(releaseService.currentReleaseSeq(planId, deptId, templateCode));
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        return Long.valueOf(String.valueOf(v));
    }
}