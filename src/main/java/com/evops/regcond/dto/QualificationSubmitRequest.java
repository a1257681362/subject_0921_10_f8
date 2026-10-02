package com.evops.regcond.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.time.LocalDate;

/**
 * 报名条件核验提交：学员材料 + 报考级别。提交时点命中的规则版本即冻结为本次口径。
 */
@Data
public class QualificationSubmitRequest {
    /** 关联报名批次（可空；正式入册必填）。 */
    private Long batchId;
    @NotBlank(message = "报名号不能为空")
    private String regNo;
    @NotBlank(message = "姓名不能为空")
    private String name;
    /** 证件类型，缺省 ID_CARD。 */
    private String idType;
    private String idNo;
    private String phone;
    private String subject;
    /** 项目编码；为空时取批次项目或 DEFAULT。 */
    private String projectCode;
    @NotBlank(message = "报考级别不能为空")
    private String levelCode;

    // ---- 材料 ----
    /** 申报学历编码。 */
    private String education;
    /** 从业起始日期（年限算头算尾的起点）。 */
    private LocalDate workStartDate;
    /** 前置证书编号。 */
    private String prereqCertNo;

    /** 提交日期（可空，缺省当天）；规则按此时点生效，年限也算到这一天。 */
    private LocalDate submittedAt;

    private String remark;
}
