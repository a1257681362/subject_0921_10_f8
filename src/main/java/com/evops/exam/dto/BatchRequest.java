package com.evops.exam.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class BatchRequest {
    @NotBlank(message = "批次编码不能为空")
    private String code;
    @NotBlank(message = "批次名称不能为空")
    private String name;
    @NotNull(message = "考点不能为空")
    private Long siteId;
    /** 项目编码；为空按 DEFAULT。助考项目传 ZHUKAO。 */
    private String projectCode;
    private String subject;
    private LocalDate examDate;
    private LocalTime startTime;
    private LocalTime endTime;
    /** 成绩复核窗口起止，跟批次配置走；可在建批次时一并给出，也可稍后用专用接口配置。 */
    private LocalDateTime reviewStartAt;
    private LocalDateTime reviewEndAt;
    private String remark;
}
