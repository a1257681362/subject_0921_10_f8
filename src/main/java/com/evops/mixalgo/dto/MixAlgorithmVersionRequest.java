package com.evops.mixalgo.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Data
public class MixAlgorithmVersionRequest {
    @Valid
    @NotNull(message = "算法参数不能为空")
    private MixParams params;
    private String remark;
}
