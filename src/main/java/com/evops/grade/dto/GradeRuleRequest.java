package com.evops.grade.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;

/**
 * 新建等级规则模块。
 */
@Data
public class GradeRuleRequest {
    @NotBlank(message = "规则编码不能为空")
    private String code;
    @NotBlank(message = "规则名称不能为空")
    private String name;
    /** 项目编码；为空按 DEFAULT。助考项目传 ZHUKAO。 */
    private String projectCode;
    /** 适用科目；为空表示项目通用规则。 */
    private String subject;
    @Positive(message = "满分必须大于 0")
    private Integer maxScore;
    private String remark;
}
