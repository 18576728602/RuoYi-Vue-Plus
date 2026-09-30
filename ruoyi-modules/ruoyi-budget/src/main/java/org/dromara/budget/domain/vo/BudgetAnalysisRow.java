package org.dromara.budget.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算执行差异归因明细行
 *
 * @author Lion Li
 * @date 2026-09-14
 */
@Data
public class BudgetAnalysisRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 归因维度键: 单位ID 或 科目编码
     */
    private String key;

    /**
     * 名称: 单位名 或 科目名
     */
    private String name;

    /**
     * 编码: 科目编码(科目维度), 单位维度为空
     */
    private String code;

    /**
     * 预算模板编码(按表归因时)
     */
    private String templateCode;

    /**
     * 模板名称(按表归因时)
     */
    private String templateName;

    /**
     * 预算额(万元)
     */
    private BigDecimal budget;

    /**
     * 执行额(万元)
     */
    private BigDecimal exec;

    /**
     * 差异额(万元,预算-执行)
     */
    private BigDecimal diff;

    /**
     * 差异率%
     */
    private BigDecimal diffRate;

    /**
     * 执行率%
     */
    private BigDecimal execRate;

    /**
     * 贡献度%(|差异额| / 总|差异额|)
     */
    private BigDecimal contribution;

    /**
     * 是否超支(执行>预算)
     */
    private Boolean overrun;
}