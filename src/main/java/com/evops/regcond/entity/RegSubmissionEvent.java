package com.evops.regcond.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 提交流转事件：提交/自动通过/转人工/人工复核/申诉改判，只追加。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_reg_submission_event")
public class RegSubmissionEvent extends BaseEntity {
    private Long submissionId;
    /** SUBMIT / AUTO_PASS / TO_MANUAL / MANUAL_PASS / MANUAL_FAIL / APPEAL_START / APPEAL_APPROVED / APPEAL_REJECTED */
    private String action;
    private String fromStatus;
    private String toStatus;
    private String detail;
    private String operator;
    private LocalDateTime eventAt;
}
