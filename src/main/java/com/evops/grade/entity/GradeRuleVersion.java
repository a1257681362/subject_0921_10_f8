package com.evops.grade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 等级规则版本：卷面分按分数段落等级；发布即冻结，定级成绩保留版本号，口径可追溯。
 * 状态：DRAFT 草稿 / ACTIVE 生效 / DISABLED 停用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_grade_rule_version")
public class GradeRuleVersion extends BaseEntity {
    private Long ruleId;
    private String versionNo;
    /** DRAFT / ACTIVE / DISABLED */
    private String status;
    /** 发布时冻结的分数段 JSON。 */
    private String gradeBands;
    private String remark;
    private String publishedBy;
    private LocalDateTime publishedAt;
    private String disabledBy;
    private LocalDateTime disabledAt;
    private String operator;
}
