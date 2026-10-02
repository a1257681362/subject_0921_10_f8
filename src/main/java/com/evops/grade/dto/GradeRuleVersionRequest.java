package com.evops.grade.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 新建规则草稿版本：提交一组分数段，发布时冻结。
 */
@Data
public class GradeRuleVersionRequest {
    @NotEmpty(message = "分数段不能为空")
    @Valid
    private List<GradeBand> bands;
    private String remark;
}
