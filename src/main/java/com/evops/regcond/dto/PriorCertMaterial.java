package com.evops.regcond.dto;

import lombok.Data;

/**
 * 前置证书材料（按配置 itemKey 对应）：
 * - 给 certNo 时先查系统内部证书（真伪、作废状态、签发日），查不到存疑转人工；
 * - external=true 为外部机构证书，按填报签发日/到期日核验，材料存疑直接转人工。
 */
@Data
public class PriorCertMaterial {
    /** 对应规则版本中的前置证书标识，必填。 */
    private String itemKey;
    /** 证书编号：给了就先查内部证书库。 */
    private String certNo;
    /** 是否外部机构证书。 */
    private Boolean external;
    /** 证书签发日 yyyy-MM-dd。 */
    private String issuedDate;
    /** 证书到期日 yyyy-MM-dd；为空且规则未规定 validYears 时存疑转人工。 */
    private String expiresDate;
    /** 材料存疑标记：印章/编号/日期看不清等，机器不判，直接转人工。 */
    private Boolean documentDoubt;
    /** 持证人姓名（内部证书比对用），缺省用提交人姓名。 */
    private String holderName;
    /** 持证人证件号（内部证书比对用，可空）。 */
    private String idNo;
}
