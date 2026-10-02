package com.evops.regcond.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class RegRuleRequest {
    @NotBlank(message = "规则编码不能为空")
    private String code;
    @NotBlank(message = "规则名称不能为空")
    private String name;
    /** 项目编码，缺省 DEFAULT。 */
    private String projectCode;
    @NotBlank(message = "报考级别不能为空")
    private String levelCode;
    /** 年限起算口径：WORK_START_DATE 工作起始日 / LAST_CERT_DATE 前一证书取得日；缺省工作起始日。 */
    private String yearsBasis;
    private String remark;
}
