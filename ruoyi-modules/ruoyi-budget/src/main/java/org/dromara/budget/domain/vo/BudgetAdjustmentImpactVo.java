package org.dromara.budget.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.math.BigDecimal;

/**
 * 预算调整影响分析结果
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
public class BudgetAdjustmentImpactVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 科目编码
     */
    private String itemCode;

    /**
     * 科目名称
     */
    private String itemName;

    /**
     * 预算表编号
     */
    private String templateCode;

    /**
     * 预算表名称
     */
    private String templateName;

    // ==================== 本公司影响 ====================
    /**
     * 调整前预算(万元)
     */
    private BigDecimal ownOriginal;

    /**
     * 调整额(万元,正/负)
     */
    private BigDecimal adjustAmount;

    /**
     * 调整后预算(万元)
     */
    private BigDecimal ownAdjusted;

    /**
     * 调整幅度%(相对于原预算)
     */
    private BigDecimal ownChangeRate;

    /**
     * 本表调整前合计(万元,该单位该表已审批科目预算之和)
     */
    private BigDecimal tableTotalBefore;

    /**
     * 本表调整后合计(万元)
     */
    private BigDecimal tableTotalAfter;

    /**
     * 本表变动率%
     */
    private BigDecimal tableChangeRate;

    /**
     * 调整后占本表合计比例%
     */
    private BigDecimal ownShareAfter;

    // ==================== 集团影响 ====================
    /**
     * 集团该科目调整前总额(万元,含本单位)
     */
    private BigDecimal groupTotalBefore;

    /**
     * 集团该科目调整后总额(万元)
     */
    private BigDecimal groupTotalAfter;

    /**
     * 集团该科目变动率%
     */
    private BigDecimal groupChangeRate;

    /**
     * 涉及(有该科目预算的)公司数
     */
    private Long companyCount;

    /**
     * 调整后本单位占集团该科目比例%
     */
    private BigDecimal groupShareAfter;

    // ==================== 历史调整追溯 ====================
    /**
     * 历史调整次数(该科目)
     */
    private Long historyCount;

    /**
     * 历史累计调整金额(万元)
     */
    private BigDecimal historyTotalAmount;

    /**
     * 历史批准调整次数
     */
    private Long historyApprovedCount;

    /**
     * 最近调整时间
     */
    private Date lastAdjustDate;
}