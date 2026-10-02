package com.evops.absence.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 补录审批：审批人取经办头 X-Operator，须非补录提交人；驳回必须写意见。
 */
@Data
public class SupplementApproveRequest {

    @NotNull(message = "审批结论不能为空（approved=true 批准生效 / false 驳回）")
    private Boolean approved;

    /** 审批意见；驳回时必填。 */
    private String comment;
}
