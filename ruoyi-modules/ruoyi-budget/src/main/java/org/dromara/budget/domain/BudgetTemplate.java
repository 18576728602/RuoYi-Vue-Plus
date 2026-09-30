package org.dromara.budget.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;

@Data
@TableName("budget_template")
public class BudgetTemplate implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String templateCode;

    /** 预算年度 */
    private Integer budgetYear;

    private String templateName;

    /** 表类型:BASE=基础表/TEXT=文本表/SPECIAL=特种表 */
    private String templateType;

    /** 实际完成列名(画布表头,默认上一年实际完成值) */
    private String actualLabel;

    /** 预算值列名(画布表头,展示为 {年度}年{列名},默认预算值(编制)) */
    private String budgetLabel;

    /** 表说明(用途) */
    private String remark;

    private Integer sortOrder;

    private String status;

    private String tenantId;

    private Long createDept;

    private Long createBy;

    private java.util.Date createTime;

    private Long updateBy;

    private java.util.Date updateTime;

    private Integer version;

    private String delFlag;

    /** 该预算表下的科目数（非表字段，用于卡片展示） */
    @TableField(exist = false)
    private Long subjectCount;

    /** 复制目标年份（非表字段，复制接口使用） */
    @TableField(exist = false)
    private Integer targetYear;
}
