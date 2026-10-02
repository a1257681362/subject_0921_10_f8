package com.evops.regcond.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 条件规则版本：核验项发布即冻结；DRAFT/ACTIVE/DISABLED，同模块同时刻仅一版生效。
 * 每次报名提交永久记下命中的版本（规则按提交时点生效，之后规则升级不改历史口径）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_condition_rule_version")
public class ConditionRuleVersion extends BaseEntity {
    private Long ruleId;
    private String versionNo;
    /** DRAFT / ACTIVE / DISABLED */
    private String status;
    /** 发布时冻结的核验项 JSON。 */
    private String items;
    private String remark;
    private String publishedBy;
    private LocalDateTime publishedAt;
    private String disabledBy;
    private LocalDateTime disabledAt;
    private String operator;
}
