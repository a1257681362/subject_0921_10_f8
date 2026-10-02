package com.evops.grade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 站内通知：复核结论、超窗关闭都通知学员。本版先落库，外发渠道（短信等）下版再接。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_score_notification")
public class ScoreNotification extends BaseEntity {
    private Long batchId;
    private Long registrationId;
    private String regNo;
    private String name;
    /** REVIEW_RESULT 复核结论 / REVIEW_CLOSED 超窗关闭 */
    private String notifyType;
    private String title;
    private String content;
    /** NEW 未读 / READ 已读 */
    private String status;
    private Long reviewId;
    private String operator;
    private LocalDateTime notifiedAt;
    private LocalDateTime readAt;
}
