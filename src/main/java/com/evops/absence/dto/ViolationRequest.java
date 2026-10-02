package com.evops.absence.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 违纪记录：直接登记，作为自动标缺考（VIOLATION 类型）的输入。
 */
@Data
public class ViolationRequest {

    @NotNull(message = "批次不能为空")
    private Long batchId;

    @NotBlank(message = "报名号不能为空")
    private String regNo;

    @NotBlank(message = "违纪类型不能为空（CHEAT 作弊 / IMPERSONATE 替考 / PROHIBITED 携带违禁品 / DISORDER 扰乱秩序 / OTHER 其他）")
    private String violationType;

    private String description;

    /** 违纪发生时间，缺省当前时间。 */
    private String occurredAt;
}
