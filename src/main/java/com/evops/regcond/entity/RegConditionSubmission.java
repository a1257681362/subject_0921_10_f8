package com.evops.regcond.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 报名条件提交记录：一次提交一条；核验结论、规则版本号在提交时点永久冻结。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_reg_condition_submission")
public class RegConditionSubmission extends BaseEntity {
    private String submissionNo;
    private String projectCode;
    private String levelCode;
    private Long batchId;
    private Long registrationId;
    private String regNo;
    private String name;
    /** 校验基准时刻：规则按提交时点生效，年限算头算尾以此日（含尾）计算。 */
    private LocalDateTime submittedAt;
    private Long ruleId;
    private Long ruleVersionId;
    private String ruleVersionNo;
    /** PASS / FAIL / MANUAL_PENDING / APPEAL_PASS / REJECTED_FAIL */
    private String status;
    private Integer failCount;
    private Integer doubtCount;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    /** 存疑项人工复核的最终结论：PASS / FAIL。 */
    private String manualOutcome;
    private String operator;
    private String remark;
}
