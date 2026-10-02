package com.evops.grade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成绩复核申请：窗口起止跟批次配置；超窗未处理由定时任务自动关闭。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_score_review")
public class ScoreReview extends BaseEntity {
    private Long scoreId;
    private Long batchId;
    private Long registrationId;
    private String regNo;
    private String name;
    private String applyReason;
    private String applicant;
    private LocalDateTime appliedAt;
    /** SUBMITTED / APPROVED / REJECTED / CLOSED */
    private String status;
    private String conclusion;
    private String reviewer;
    private LocalDateTime concludedAt;
    /** 结论时改分前后快照，与 t_score_change_log 互为印证。 */
    private BigDecimal scoreBefore;
    private String gradeBefore;
    private BigDecimal scoreAfter;
    private String gradeAfter;
}
