package com.evops.vault.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 卷箱建档：箱号先建档再登记出入。
 */
@Data
public class BoxRequest {

    @NotBlank(message = "箱号不能为空")
    private String boxNo;

    private String name;
    private String remark;
}
