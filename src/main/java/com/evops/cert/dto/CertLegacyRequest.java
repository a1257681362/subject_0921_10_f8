package com.evops.cert.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 手工旧号补登：把已手工发出、收不回的旧证登记进系统，不重新发号。
 * 登记后系统发号流水自动抬过旧号（库内已用最大流水兜底），不撞旧号。
 */
@Data
public class CertLegacyRequest {
    /** 旧证编号（原样登记，不强制符合当前规则形态；全局唯一）。 */
    @NotBlank(message = "旧证编号不能为空")
    private String certNo;
    @NotBlank(message = "持证人姓名不能为空")
    private String holderName;
    private String idNo;
    /** 项目编码；为空按 DEFAULT。 */
    private String projectCode;
    private Long batchId;
    /** 能从旧号辨认时填写，用于抬高对应规则序列防撞；无法辨认可空。 */
    private Integer certYear;
    private String certBatch;
    private Integer certSeq;
    private String subject;
    private String grade;
    /** 有效期起（可空）。 */
    private java.time.LocalDate validFrom;
    /** 有效期止（可空=长期有效）。 */
    private java.time.LocalDate validTo;
    private String remark;
}
