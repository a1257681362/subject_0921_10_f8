package com.evops.regcond.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 申诉受理后的终裁结论。
 */
@Data
public class AppealConclusionRequest {
    @NotNull(message = "结论不能为空（approved=true/false）")
    private Boolean approved;
    /** 驳回必须写意见；通过可写说明。 */
    private String conclusion;
}
