package com.evops.appoint.vo;

import lombok.Data;

/**
 * 排班生成聘任的预检/结果逐项：展示某条排班能否生成聘任及金额口径。
 */
@Data
public class ShiftPreviewItem {
    /** 业务键：考场号 + 角色。 */
    private String key;
    private String roomNo;
    private String roleCode;
    private String invigilatorCode;
    private String invigilatorName;
    /** 命中的津贴标准编码与单场金额。 */
    private String rateCode;
    private String amount;
    /** 已存在聘任时的当前状态。 */
    private String currentStatus;
    private boolean actionable;
    private String note;
}
