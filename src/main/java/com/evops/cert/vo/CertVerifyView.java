package com.evops.cert.vo;

import lombok.Data;

/**
 * 对外验真结果：只回有效性与批次（证面公开项），不含证件号等隐私字段。
 * 编号不存在或姓名不匹配统一 found=false，不暴露编号是否存在，防止枚举探测。
 */
@Data
public class CertVerifyView {
    /** 是否查到与“编号+姓名”匹配的证书。 */
    private boolean found;
    /** 是否有效：ISSUED 为 true；已作废为 false。 */
    private boolean valid;
    /** ISSUED 有效 / REVOKED 已作废。 */
    private String status;
    /** 面向查询单位的明确中文结论。 */
    private String message;
    private String certNo;
    /** 回显姓名用于人证一致确认（验真请求本就提供姓名）。 */
    private String holderName;
    private String batchCode;
    private String batchName;
    private String subject;
    private String grade;
    private String projectCode;
    private String issuedAt;
    /** 仅作废时给出作废说明（明确提示），不含其他隐私。 */
    private String revokeReason;
}
