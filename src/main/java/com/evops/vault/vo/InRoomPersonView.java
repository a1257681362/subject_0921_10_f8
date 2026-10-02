package com.evops.vault.vo;

import lombok.Data;

/**
 * 在室人员（实时）：按已生效登记推导，最后一条生效方向为 IN 即在室。
 */
@Data
public class InRoomPersonView {
    private String personCode;
    private String personName;
    /** 本次进室时间（最后一条生效 IN 登记的发生时间）。 */
    private String sinceAt;
    private Long accessId;
    private String accessNo;

    public InRoomPersonView() {
    }

    public InRoomPersonView(String personCode, String personName, String sinceAt, Long accessId, String accessNo) {
        this.personCode = personCode;
        this.personName = personName;
        this.sinceAt = sinceAt;
        this.accessId = accessId;
        this.accessNo = accessNo;
    }
}
