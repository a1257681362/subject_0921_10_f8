package com.evops.exam.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class RegistrationRequest {
    @NotBlank(message = "报名号不能为空")
    private String regNo;
    @NotBlank(message = "姓名不能为空")
    private String name;
    private String idType;
    private String idNo;
    private String gender;
    private String phone;
    private String subject;
    /** 所属学校（机构），用于同校学员混排错开；可空，空按未知学校处理 */
    private String school;
    private String remark;
}
