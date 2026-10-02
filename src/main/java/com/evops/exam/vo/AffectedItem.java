package com.evops.exam.vo;

import lombok.Data;

/**
 * 批量动作影响范围项。
 */
@Data
public class AffectedItem {
    private String key;
    private String name;
    private String currentStatus;
    private boolean actionable;
    private String note;

    public AffectedItem() {
    }

    public AffectedItem(String key, String name, String currentStatus, boolean actionable, String note) {
        this.key = key;
        this.name = name;
        this.currentStatus = currentStatus;
        this.actionable = actionable;
        this.note = note;
    }
}
