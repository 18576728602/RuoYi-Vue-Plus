package org.dromara.budget.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 预算业务分类视图对象
 *
 * @author Lion Li
 * @date 2026-09-28
 */
@Data
@ExcelIgnoreUnannotated
public class BudgetCategoryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID(雪花)
     */
    private Long id;

    /**
     * 分类编码(业务关联唯一键)
     */
    @ExcelProperty(value = "分类编码")
    private String categoryCode;

    /**
     * 分类名称
     */
    @ExcelProperty(value = "分类名称")
    private String categoryName;

    /**
     * 分类下平铺的明细科目数量(由调用方回填,用于列表展示)
     */
    private Integer subjectCount;

    /**
     * 排序
     */
    @ExcelProperty(value = "排序")
    private Integer sort;

    /**
     * 备注
     */
    @ExcelProperty(value = "备注")
    private String remark;

    /**
     * 创建时间
     */
    @ExcelProperty(value = "创建时间")
    private Date createTime;

    /**
     * 更新时间
     */
    @ExcelProperty(value = "更新时间")
    private Date updateTime;
}