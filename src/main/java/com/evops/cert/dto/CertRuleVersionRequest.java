package com.evops.cert.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * 新建编号规则草稿版本：带发号参数，发布时冻结。
 */
@Data
public class CertRuleVersionRequest {
    @Valid
    @NotNull(message = "发号参数 params 不能为空")
    private CertRuleConfig params;
    private String remark;
}
