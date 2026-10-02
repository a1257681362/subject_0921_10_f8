package com.evops.cert.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 发证清单项：持证人信息。发证后只读，作废只留记录。
 */
@Data
public class CertIssueItem {
    @NotBlank(message = "持证人姓名不能为空")
    private String holderName;
    /** 证件号（可选，仅内部留档，验真不回显）；同年度批次下用于同人防重。 */
    private String idNo;
    private String subject;
    private String grade;
    /** 有效期起（可空）。 */
    private java.time.LocalDate validFrom;
    /** 有效期止（可空=长期有效）。 */
    private java.time.LocalDate validTo;
}
