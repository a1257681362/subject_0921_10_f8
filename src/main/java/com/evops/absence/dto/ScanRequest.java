package com.evops.absence.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 自动标缺考扫描：按签到与违纪记录对批次名册扫描，预检 → 执行两步。
 */
@Data
public class ScanRequest {

    @NotNull(message = "批次不能为空")
    private Long batchId;
}
