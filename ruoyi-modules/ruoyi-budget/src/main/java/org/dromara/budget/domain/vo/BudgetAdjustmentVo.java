package org.dromara.budget.domain.vo;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.dromara.budget.domain.BudgetAdjustment;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;



/**
 * 预算调整记录视图对象 budget_adjustment
 *
 * @author Lion Li
 * @date 2026-08-29
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BudgetAdjustment.class)
public class BudgetAdjustmentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @ExcelProperty(value = "主键ID")
    private Long id;

    /**
     * 预算方案ID
     */
    @ExcelProperty(value = "预算方案ID")
    private Long planId;

    /**
     * 预算方案名称(联表补充，非表字段)
     */
    @ExcelProperty(value = "预算方案")
    private String planName;

    /**
     * 调整单位
     */
    @ExcelProperty(value = "调整单位")
    private Long orgId;

    /**
     * 预算表编号
     */
    @ExcelProperty(value = "预算表编号")
    private String templateCode;

    /**
     * 调整科目编码
     */
    @ExcelProperty(value = "调整科目编码")
    private String itemCode;

    /**
     * 原预算金额
     */
    @ExcelProperty(value = "原预算金额")
    private BigDecimal originalAmount;

    /**
     * 调整金额(正数增加,负数减少)
     */
    @ExcelProperty(value = "调整金额(正数增加,负数减少)")
    private BigDecimal adjustAmount;

    /**
     * 调整后金额
     */
    @ExcelProperty(value = "调整后金额")
    private BigDecimal adjustedAmount;

    /**
     * 调整日期
     */
    @ExcelProperty(value = "调整日期")
    private Date adjustDate;

    /**
     * 调整原因
     */
    @ExcelProperty(value = "调整原因")
    private String reason;

    /**
     * 附件地址(多个用逗号分隔，非必填)
     */
    private String attachment;

    /**
     * 状态: PENDING=待审批, APPROVED=已通过, REJECTED=已驳回
     */
    @ExcelProperty(value = "状态: PENDING=待审批, APPROVED=已通过, REJECTED=已驳回")
    private String status;

    /**
     * 申请人
     */
    @ExcelProperty(value = "申请人")
    private Long applicantId;

    /**
     * 申请人姓名
     */
    @ExcelProperty(value = "申请人姓名")
    private String applicantName;

    /**
     * 审批人
     */
    @ExcelProperty(value = "审批人")
    private Long approverId;

    /**
     * 审批人姓名
     */
    @ExcelProperty(value = "审批人姓名")
    private String approverName;

    /**
     * 审批时间
     */
    @ExcelProperty(value = "审批时间")
    private Date approveTime;

    /**
     * 审批意见
     */
    @ExcelProperty(value = "审批意见")
    private String approveRemark;


}
