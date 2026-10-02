package com.evops.vault.vo;

import lombok.Data;

/**
 * 纸面存量导入预检项。
 */
@Data
public class ImportPreviewItem {
    private String key;
    private String personCode;
    private String personName;
    private String direction;
    private String occurredAt;
    private Integer boxCount;
    /** 导入后是否落在异常时段（落即自动标记并告警保卫）。 */
    private Boolean abnormal;
    private boolean actionable;
    private String note;

    public ImportPreviewItem() {
    }
}
