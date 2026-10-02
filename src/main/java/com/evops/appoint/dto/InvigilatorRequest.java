package com.evops.appoint.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 兼职监考人员建档。
 */
@Data
public class InvigilatorRequest {
    @NotBlank(message = "监考人员编码不能为空")
    private String code;
    @NotBlank(message = "监考人员姓名不能为空")
    private String name;
    /** CHIEF 主考 / INVIGILATOR 监考 / PATROL 巡考；缺省监考。 */
    private String roleCode;
    private String phone;
    /** PART_TIME 兼职 / FULL_TIME 在编；缺省兼职。 */
    private String staffKind;
    private String remark;
}
