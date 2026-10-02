package com.evops.grade.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 学员提交成绩复核：可按成绩 id，或按批次 + 报名号定位。
 */
@Data
public class ReviewApplyRequest {
    private Long scoreId;
    private Long batchId;
    private String regNo;
    /** 申请理由 / 疑问说明。 */
    private String reason;
    /** 申请人；缺省取 X-Operator。 */
    private String applicant;
}
