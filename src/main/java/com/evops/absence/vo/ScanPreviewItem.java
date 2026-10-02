package com.evops.absence.vo;

import lombok.Data;

/**
 * 自动标缺考预检项：逐条列可标记 / 跳过与原因。
 */
@Data
public class ScanPreviewItem {
    private String regNo;
    private String name;
    /** ABSENT 缺考 / VIOLATION 违纪。 */
    private String markType;
    private String source;
    private boolean actionable;
    private String note;

    public ScanPreviewItem() {
    }

    public ScanPreviewItem(String regNo, String name, String markType, String source,
                           boolean actionable, String note) {
        this.regNo = regNo;
        this.name = name;
        this.markType = markType;
        this.source = source;
        this.actionable = actionable;
        this.note = note;
    }
}
