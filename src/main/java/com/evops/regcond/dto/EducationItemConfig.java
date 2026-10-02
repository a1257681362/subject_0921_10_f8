package com.evops.regcond.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 学历核验项：requiredLevel 为层次编码（HIGH_SCHOOL/JUNIOR_COLLEGE/BACHELOR/MASTER/DOCTOR），
 * 学员实际学历层次达到或超过要求即通过。
 */
@Data
public class EducationItemConfig {
    /** 是否启用（false/null 等价不核验）。 */
    private Boolean enabled;
    @NotBlank(message = "学历要求层次不能为空")
    private String requiredLevel;
    /** 核验项展示名，缺省"学历"。 */
    private String label;
}
