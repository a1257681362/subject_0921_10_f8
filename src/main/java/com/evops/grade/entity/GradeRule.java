package com.evops.grade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 等级规则模块：一个项目一套规则（可按科目细分），规则下挂多个版本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_grade_rule")
public class GradeRule extends BaseEntity {
    private String code;
    private String name;
    /** 项目编码，与批次 project_code 对齐。 */
    private String projectCode;
    /** 适用科目；为空表示项目通用规则。 */
    private String subject;
    private Integer maxScore;
    private String remark;
}
