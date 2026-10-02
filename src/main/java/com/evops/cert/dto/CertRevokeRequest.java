package com.evops.cert.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 证书作废请求：发证后信息只读，作废不删除、不改信息，只追加作废记录并切状态。
 */
@Data
public class CertRevokeRequest {
    @NotBlank(message = "作废原因不能为空")
    private String reason;
}
