package com.evops.appoint.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 换人：把某条聘任的当前监考人员换下，由接任者承担本场。原持聘人留 REPLACED 版本，不计费。
 */
@Data
public class ReplaceRequest {
    /** 接任监考人员编码。 */
    @NotBlank(message = "接任监考人员编码不能为空")
    private String toInvigilatorCode;
    /** 换人原因。 */
    private String reason;
}
