package com.evops.cert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 编号规则版本：发布即冻结发号参数，已发证书保留版本号，规则升级只管新证。
 * 状态：DRAFT 草稿 / ACTIVE 生效 / DISABLED 停用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_cert_number_rule_version")
public class CertNumberRuleVersion extends BaseEntity {
    private Long ruleId;
    private String versionNo;
    /** DRAFT / ACTIVE / DISABLED */
    private String status;
    /** 发布时冻结的发号参数 JSON。 */
    private String params;
    private String remark;
    private String publishedBy;
    private LocalDateTime publishedAt;
    private String disabledBy;
    private LocalDateTime disabledAt;
    private String operator;
}
