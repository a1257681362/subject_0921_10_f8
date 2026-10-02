package com.evops.exam.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 配置批次成绩复核窗口：起止跟批次配置走。
 */
@Data
public class ReviewWindowRequest {
    @NotNull(message = "复核窗口开始时间不能为空")
    private LocalDateTime startAt;
    @NotNull(message = "复核窗口结束时间不能为空")
    private LocalDateTime endAt;
}
