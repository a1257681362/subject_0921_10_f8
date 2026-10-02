package com.evops.grade.dto;

import lombok.Data;

import java.math.BigDecimal;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 成绩录入项：按报名号定位名册（报名号全局唯一）。
 */
@Data
public class ScoreImportItem {
    @NotBlank(message = "报名号不能为空")
    private String regNo;
    @NotNull(message = "卷面分不能为空")
    private BigDecimal paperScore;
    private String remark;
}
