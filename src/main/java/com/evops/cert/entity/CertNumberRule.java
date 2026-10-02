package com.evops.cert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编号规则模块：一个项目一套规则（缺省 DEFAULT），规则下挂多个版本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_cert_number_rule")
public class CertNumberRule extends BaseEntity {
    private String code;
    private String name;
    /** 项目编码，与批次 project_code 对齐。 */
    private String projectCode;
    private String remark;
}
