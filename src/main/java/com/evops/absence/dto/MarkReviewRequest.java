package com.evops.absence.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 缺考裁定复核（双人复核）：复核人取经办头 X-Operator，须非标记经办人；
 * 可按需修正拟定结论（scoreValid 等字段留空则按拟定值确认）；驳回必须写意见。
 */
@Data
public class MarkReviewRequest {

    @NotNull(message = "复核结论不能为空（approved=true 确认生效 / false 驳回）")
    private Boolean approved;

    /** 复核意见；驳回时必填。 */
    private String comment;

    /** 复核修正：标记类型（留空按拟定值；缺考/弃考/违纪可在此纠正分清）。 */
    private String markType;

    /** 复核修正：成绩是否有效（留空按拟定值）。 */
    private Boolean scoreValid;

    /** 复核修正：无效原因。 */
    private String invalidReason;

    /** 复核修正：衔接下次安排。 */
    private String nextArrangement;

    /** 复核修正：衔接下次批次。 */
    private Long nextBatchId;
}
