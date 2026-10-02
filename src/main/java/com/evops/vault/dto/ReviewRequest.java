package com.evops.vault.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 第二人复核：复核人必须是登记者本人以外的在册启用人员；驳回必须写意见。
 */
@Data
public class ReviewRequest {

    @NotBlank(message = "复核人编码不能为空")
    private String reviewerCode;

    @NotNull(message = "复核结论不能为空（approved=true 确认生效 / false 驳回）")
    private Boolean approved;

    /** 复核意见；驳回时必填。 */
    private String comment;
}
