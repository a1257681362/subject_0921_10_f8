package com.evops.regcond.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class RegAppealConclusionRequest {
    /** true 申诉成立改判通过 / false 申诉驳回维持不通过。 */
    @NotNull(message = "申诉结论不能为空")
    private Boolean approved;
    @NotNull(message = "申诉结论说明不能为空")
    private String conclusion;
}
