package com.evops.cert.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 新建编号规则模块。
 */
@Data
public class CertRuleRequest {
    @NotBlank(message = "规则编码不能为空")
    private String code;
    @NotBlank(message = "规则名称不能为空")
    private String name;
    /** 项目编码；为空按 DEFAULT。 */
    private String projectCode;
    private String remark;
}
