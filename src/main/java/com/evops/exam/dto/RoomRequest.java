package com.evops.exam.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class RoomRequest {
    @NotNull(message = "考点不能为空")
    private Long siteId;
    @NotBlank(message = "考场号不能为空")
    private String roomNo;
    private String building;
    private Integer floorNo;
    @NotNull(message = "容量不能为空")
    @Min(value = 1, message = "容量必须大于 0")
    private Integer capacity;
    private String locationDesc;
}
