package com.evops.vault.vo;

import lombok.Data;

/**
 * 允许进出时段回显：HH:mm，起点含、终点不含；窗口外登记自动标记并告警保卫。
 */
@Data
public class AccessWindowView {
    private String start;
    private String end;

    public AccessWindowView() {
    }

    public AccessWindowView(String start, String end) {
        this.start = start;
        this.end = end;
    }
}
