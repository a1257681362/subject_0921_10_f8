package com.evops.exam.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class SiteRequest {
    @NotBlank(message = "考点编码不能为空")
    private String code;
    @NotBlank(message = "考点名称不能为空")
    private String name;
    private String address;
    private String contactPerson;
    private String contactPhone;
    private String remark;
}
