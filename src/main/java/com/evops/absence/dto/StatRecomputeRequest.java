package com.evops.absence.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 手动重算批次缺考统计（平时由裁定/签到变动自动重算）。
 */
@Data
public class StatRecomputeRequest {

    @NotNull(message = "批次不能为空")
    private Long batchId;
}
