package com.evops.regcond.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 条件规则模块：按项目 + 级别建档（学历、年限、前置证书按级别各配一套），规则下挂多个版本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_condition_rule")
public class ConditionRule extends BaseEntity {
    private String code;
    private String name;
    /** 项目编码，与批次 project_code 对齐。 */
    private String projectCode;
    /** 适用级别（如 LEVEL_1）。 */
    private String levelCode;
    private String remark;
}
