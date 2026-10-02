package com.evops.absence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成绩快照（逐人逐笔）：裁定确认时记该生成绩的前后状态，只追加。
 * 两个窗口对同一笔裁定查到同一份成绩留痕。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_score_snapshot")
public class ScoreSnapshot extends BaseEntity {
    private Long batchId;
    private Long registrationId;
    private String regNo;
    private String name;
    /** 裁定落定时成绩行的样子（无成绩行则分数/等级为空）。 */
    private Long scoreId;
    private BigDecimal paperScore;
    private String grade;
    private String ruleVersionNo;
    private Boolean validBefore;
    private Boolean validAfter;
    private Long markId;
    private String markNo;
    private String markType;
    private String changeReason;
    private String operator;
    private LocalDateTime snapshottedAt;
}
