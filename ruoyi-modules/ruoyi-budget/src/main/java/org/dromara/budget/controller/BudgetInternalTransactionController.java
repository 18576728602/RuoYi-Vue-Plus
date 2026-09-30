package org.dromara.budget.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.budget.service.IBudgetInternalTransactionService;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 内部交易登记 Controller
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/budget/internal")
public class BudgetInternalTransactionController {

    private final IBudgetInternalTransactionService internalTransactionService;

    /**
     * 分页查询内部交易列表
     */
    @GetMapping("/list")
    public TableDataInfo<Map<String, Object>> list(
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String status,
            PageQuery pageQuery) {
        return internalTransactionService.queryList(planId, transactionType, status, pageQuery);
    }

    /**
     * 新增内部交易
     */
    @PostMapping
    public R<Void> add(@RequestBody Map<String, Object> params) {
        return internalTransactionService.insert(params) ? R.ok() : R.fail("新增失败");
    }

    /**
     * 修改内部交易
     */
    @PutMapping
    public R<Void> edit(@RequestBody Map<String, Object> params) {
        return internalTransactionService.update(params) ? R.ok() : R.fail("修改失败");
    }

    /**
     * 删除内部交易
     */
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        return internalTransactionService.deleteById(id) ? R.ok() : R.fail("删除失败");
    }

    /**
     * 批量删除
     */
    @DeleteMapping("/batch/{ids}")
    public R<Void> removeBatch(@PathVariable Long[] ids) {
        return internalTransactionService.deleteByIds(Arrays.asList(ids)) ? R.ok() : R.fail("删除失败");
    }

    /**
     * 确认内部交易
     */
    @PutMapping("/confirm")
    public R<Void> confirm(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) params.get("ids");
        return internalTransactionService.confirm(ids) ? R.ok() : R.fail("确认失败");
    }

    /**
     * 撤销确认
     */
    @PutMapping("/revoke")
    public R<Void> revokeConfirm(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) params.get("ids");
        return internalTransactionService.revokeConfirm(ids) ? R.ok() : R.fail("撤销失败");
    }

    /**
     * 获取抵销项目配置
     */
    @GetMapping("/configs")
    public R<List<Map<String, Object>>> getConfigs(@RequestParam(required = false) String transactionType) {
        return R.ok(internalTransactionService.getEliminationConfigs(transactionType));
    }

    /**
     * 获取部门选项
     */
    @GetMapping("/depts")
    public R<List<Map<String, Object>>> getDeptOptions() {
        return R.ok(internalTransactionService.getDeptOptions());
    }
}
