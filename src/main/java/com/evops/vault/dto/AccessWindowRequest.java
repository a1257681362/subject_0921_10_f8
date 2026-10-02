package com.evops.vault.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 允许进出时段配置：HH:mm，起点含、终点不含；落在窗口外的登记自动标记并告警保卫。
 */
@Data
public class AccessWindowRequest {

    @NotBlank(message = "窗口起点不能为空（HH:mm）")
    private String start;

    @NotBlank(message = "窗口终点不能为空（HH:mm）")
    private String end;
}
