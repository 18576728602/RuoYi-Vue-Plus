package org.dromara.budget.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import org.dromara.budget.domain.BudgetApprovalTransfer;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 审批转交记录视图对象
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BudgetApprovalTransfer.class)
public class BudgetApprovalTransferVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 关联类型 FILL=预算填报 / ADJUSTMENT=预算调整
     */
    private String targetType;

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
     * 转交人ID
     */
    private Long transfererId;

    /**
     * 转交人名称
     */
    private String transfererName;

    /**
     * 接收人ID
     */
    private Long receiverId;

    /**
     * 接收人名称
     */
    private String receiverName;

    /**
     * 转交原因
     */
    private String reason;

    /**
     * 转交时间
     */
    private Date transferTime;

    /**
     * 创建时间
     */
    private Date createTime;
}