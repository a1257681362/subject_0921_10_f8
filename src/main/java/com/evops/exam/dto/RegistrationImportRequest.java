package com.evops.exam.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class RegistrationImportRequest {
    @NotNull(message = "批次不能为空")
    private Long batchId;
    @Valid
    @NotEmpty(message = "报名名册不能为空")
    private List<RegistrationRequest> registrations;
}
