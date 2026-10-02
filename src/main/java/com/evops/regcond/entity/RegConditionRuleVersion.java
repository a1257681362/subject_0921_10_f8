package com.evops.regcond.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 条件规则版本：核验项配置发布即冻结；提交时点按当时 ACTIVE 版本核验，历史提交保留版本号。
 * 状态：DRAFT 草稿 / ACTIVE 生效 / DISABLED 停用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_reg_condition_rule_version")
public class RegConditionRuleVersion extends BaseEntity {
    private Long ruleId;
    private String versionNo;
    /** DRAFT / ACTIVE / DISABLED */
    private String status;
    /** 发布时冻结的核验项 JSON。 */
    private String itemsConfig;
    private String remark;
    private String publishedBy;
    private LocalDateTime publishedAt;
    private String disabledBy;
    private LocalDateTime disabledAt;
    private String operator;
}
