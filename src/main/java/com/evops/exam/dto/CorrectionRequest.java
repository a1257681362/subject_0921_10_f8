package com.evops.exam.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class CorrectionRequest {
    /** 允许勘误字段：NAME / ID_NO / GENDER / PHONE / SUBJECT / SCHOOL */
    @NotBlank(message = "勘误字段不能为空")
    private String fieldName;
    @NotBlank(message = "更正后的值不能为空")
    private String newValue;
    private String reason;
}
