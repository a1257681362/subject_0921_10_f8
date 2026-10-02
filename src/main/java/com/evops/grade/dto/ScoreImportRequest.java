package com.evops.grade.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 按批次批量录入卷面分，先 preview 再 execute。
 */
@Data
public class ScoreImportRequest {
    @NotNull(message = "批次不能为空")
    private Long batchId;
    @NotEmpty(message = "成绩明细不能为空")
    @Valid
    private List<ScoreImportItem> scores;
}
