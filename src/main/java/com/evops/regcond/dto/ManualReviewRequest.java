package com.evops.regcond.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 存疑转人工的复核结论。
 */
@Data
public class ManualReviewRequest {
    /** true 人工放行（合格）/ false 人工拒收（不通过）。 */
    @NotNull(message = "人工结论不能为空（approved=true/false）")
    private Boolean approved;
    /** 结论说明（拒收必填原因）。 */
    private String comment;
}
