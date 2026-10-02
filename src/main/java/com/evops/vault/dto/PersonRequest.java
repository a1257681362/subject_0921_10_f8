package com.evops.vault.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 保密室准入人员建档：进出刷证按卡号或人员编码定位。
 */
@Data
public class PersonRequest {

    @NotBlank(message = "人员编码不能为空")
    private String code;

    @NotBlank(message = "姓名不能为空")
    private String name;

    @NotBlank(message = "刷证卡号不能为空")
    private String cardNo;

    private String duty;
    private String remark;
}
