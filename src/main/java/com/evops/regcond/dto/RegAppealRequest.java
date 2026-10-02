package com.evops.regcond.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class RegAppealRequest {
    /** 与 submissionNo 二选一。 */
    private Long submissionId;
    private String submissionNo;
    @NotBlank(message = "申诉理由不能为空")
    private String reason;
    /** 申诉人（学员本人或代办），缺省取报名号。 */
    private String appellant;
}
