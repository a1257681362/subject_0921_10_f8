package com.evops.regcond.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 条件规则建档：按项目 + 级别，一个级别一套规则。
 */
@Data
public class ConditionRuleRequest {
    @NotBlank(message = "规则编码不能为空")
    private String code;
    @NotBlank(message = "规则名称不能为空")
    private String name;
    /** 项目编码；为空按 DEFAULT。 */
    private String projectCode;
    @NotBlank(message = "适用级别不能为空")
    private String levelCode;
    private String remark;
}
