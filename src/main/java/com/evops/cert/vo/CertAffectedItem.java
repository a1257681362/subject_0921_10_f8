package com.evops.cert.vo;

import lombok.Data;

/**
 * 发证预检 / 执行清单项：逐条列可发 / 跳过与拟发编号或原因。
 */
@Data
public class CertAffectedItem {
    private int index;
    private String holderName;
    private boolean actionable;
    /** 预检时为拟发编号，执行后为实发编号；跳过为空。 */
    private String certNo;
    /** ISSUED 已发 / SKIPPED 跳过。 */
    private String status;
    private String reason;

    public CertAffectedItem() {
    }

    public CertAffectedItem(int index, String holderName, boolean actionable, String certNo,
                            String status, String reason) {
        this.index = index;
        this.holderName = holderName;
        this.actionable = actionable;
        this.certNo = certNo;
        this.status = status;
        this.reason = reason;
    }
}
