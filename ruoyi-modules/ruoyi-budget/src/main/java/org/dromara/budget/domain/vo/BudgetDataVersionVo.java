package org.dromara.budget.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import org.dromara.budget.domain.BudgetDataVersion;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 提交版本快照视图对象
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BudgetDataVersion.class)
public class BudgetDataVersionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
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
     * 提交版本号
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
     * 备注
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
     * 创建时间
     */
    private Date createTime;
}