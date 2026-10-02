package com.evops.cert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 证书主表：发证即只读，编号全局唯一；作废只切状态并追加作废记录，不删除、不改持证人信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_certificate")
public class Certificate extends BaseEntity {
    private String certNo;
    /** SYSTEM 系统发号 / LEGACY 手工旧号补登。 */
    private String source;
    /** 发证时冻结的规则版本（规则调整只管新证）。 */
    private Long ruleId;
    private Long ruleVersionId;
    private String ruleVersionNo;
    /** 编号要素拆分留档。 */
    private Integer certYear;
    private String certBatch;
    private Integer certSeq;
    /** 持证人姓名（验真要素）。 */
    private String holderName;
    /** 证件号（仅内部留档，验真不回显）。 */
    private String idNo;
    private String projectCode;
    private Long batchId;
    private String batchCode;
    private String batchName;
    private String subject;
    private String grade;
    /** 有效期起（可空：老证未登记/无固定起期）；报名前置证书核验连有效期一起验。 */
    private LocalDate validFrom;
    /** 有效期止（可空=长期有效）；报名提交日须落在 [validFrom, validTo] 闭区间内。 */
    private LocalDate validTo;
    private LocalDateTime issuedAt;
    private String issuedBy;
    /** ISSUED 有效 / REVOKED 已作废。 */
    private String status;
    private LocalDateTime revokedAt;
    private String revokedBy;
    private String revokeReason;
    private String remark;
}
