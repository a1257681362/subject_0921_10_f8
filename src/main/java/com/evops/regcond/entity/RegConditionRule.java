package com.evops.regcond.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报名条件规则模块：按 项目+报考级别 建档，规则下挂多个版本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_reg_condition_rule")
public class RegConditionRule extends BaseEntity {
    private String code;
    private String name;
    /** 项目编码：与批次 project_code 对齐（缺省 DEFAULT）。 */
    private String projectCode;
    /** 报考级别：同一项目不同级别各一套条件。 */
    private String levelCode;
    /** 年限起算口径：WORK_START_DATE / LAST_CERT_DATE。 */
    private String yearsBasis;
    private String remark;
}
