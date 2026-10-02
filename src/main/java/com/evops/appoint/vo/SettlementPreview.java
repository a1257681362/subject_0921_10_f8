package com.evops.appoint.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 月底结算预检：展示该月计费场次、人数、合计及是否已有草稿 / 已锁定，不落库。
 */
@Data
public class SettlementPreview {
    private String month;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer personCount;
    private Integer totalAppointments;
    private BigDecimal totalAmount;
    private String existingSettlementNo;
    private String existingStatus;
    private String note;
    private List<SettlementPreviewItem> items;
}
