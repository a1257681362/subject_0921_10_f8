package com.evops.grade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 改分留痕：改分前后都记一条，只追加，不修改不删除。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_score_change_log")
public class ScoreChangeLog extends BaseEntity {
    private Long scoreId;
    private Long reviewId;
    private Long batchId;
    private String regNo;
    private String name;
    private BigDecimal scoreBefore;
    private BigDecimal scoreAfter;
    private String gradeBefore;
    private String gradeAfter;
    private String changeReason;
    /** 重算等级所用的规则版本（与成绩原口径一致）。 */
    private String ruleVersionNo;
    private String operator;
    private LocalDateTime changedAt;
}
