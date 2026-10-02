package com.evops.absence.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 签到补录：走审批（PENDING→CONFIRMED/REJECTED），批准才生效；审批人须非补录提交人。
 */
@Data
public class SupplementRequest {

    @NotNull(message = "批次不能为空")
    private Long batchId;

    @NotBlank(message = "报名号不能为空")
    private String regNo;

    @NotBlank(message = "补录签到时间不能为空")
    private String checkInAt;

    @NotBlank(message = "补录理由不能为空")
    private String reason;

    private String remark;
}
