package com.evops.absence.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 变更裁定：仅已生效（CONFIRMED）裁定可变更；变更回到待复核，双人复核确认才生效。
 * 可一并纠正类型（缺考/弃考分清）；裁定有变更就重算统计。
 */
@Data
public class MarkChangeRequest {

    @NotNull(message = "变更后结论不能为空（scoreValid=true 成绩有效 / false 成绩无效）")
    private Boolean scoreValid;

    /** 变更类型（可选）：ABSENT 缺考 / ABANDONED 弃考 / VIOLATION 违纪。 */
    private String markType;

    /** 无效原因（scoreValid=false 时必填或沿用原值）。 */
    private String invalidReason;

    /** 衔接下次安排（scoreValid=false 时必填或沿用原值）。 */
    private String nextArrangement;

    /** 衔接下次批次（可选）。 */
    private Long nextBatchId;

    @NotBlank(message = "变更原因不能为空")
    private String reason;
}
