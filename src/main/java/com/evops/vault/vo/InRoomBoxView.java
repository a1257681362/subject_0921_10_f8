package com.evops.vault.vo;

import lombok.Data;

/**
 * 在室卷箱（实时）：按已生效卷箱出入明细推导，最后一条生效方向为 IN 即在室。
 */
@Data
public class InRoomBoxView {
    private String boxNo;
    private String boxName;
    /** 本次进室时间。 */
    private String sinceAt;
    /** 经手登记（谁带进来的）。 */
    private Long accessId;
    private String accessNo;
    private String personCode;
    private String personName;

    public InRoomBoxView() {
    }

    public InRoomBoxView(String boxNo, String boxName, String sinceAt,
                         Long accessId, String accessNo, String personCode, String personName) {
        this.boxNo = boxNo;
        this.boxName = boxName;
        this.sinceAt = sinceAt;
        this.accessId = accessId;
        this.accessNo = accessNo;
        this.personCode = personCode;
        this.personName = personName;
    }
}
