package com.evops.absence.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 手动标记：弃考（ABANDONED）等由人工登记；登记即待复核，双人复核确认才生效。
 */
@Data
public class MarkRequest {

    @NotNull(message = "批次不能为空")
    private Long batchId;

    @NotBlank(message = "报名号不能为空")
    private String regNo;

    @NotBlank(message = "标记类型不能为空（ABSENT 缺考 / ABANDONED 弃考 / VIOLATION 违纪）")
    private String markType;

    @NotBlank(message = "标记原因不能为空")
    private String reason;

    /** 衔接下次安排（裁定无效时必填，可在复核时补充）。 */
    private String nextArrangement;

    /** 衔接下次批次（可选）。 */
    private Long nextBatchId;

    private String remark;
}
