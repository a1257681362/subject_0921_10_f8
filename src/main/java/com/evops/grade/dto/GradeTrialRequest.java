package com.evops.grade.dto;

import lombok.Data;

import java.math.BigDecimal;
import javax.validation.constraints.NotNull;

/**
 * 按版本试算等级（不写成绩）。
 */
@Data
public class GradeTrialRequest {
    @NotNull(message = "卷面分不能为空")
    private BigDecimal score;
}
