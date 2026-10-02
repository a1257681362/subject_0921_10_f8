package com.evops.cert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 作废记录：只追加。证书作废时写一条，原证不删除、信息仍只读，验真时给明确作废提示。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_certificate_revoke_record")
public class CertificateRevokeRecord extends BaseEntity {
    private Long certificateId;
    private String certNo;
    private String holderName;
    private String reason;
    private String operator;
    private LocalDateTime revokedAt;
}
