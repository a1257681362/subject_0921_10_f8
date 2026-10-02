package com.evops.appoint.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 结算预检逐项：一人一行的场次与金额。
 */
@Data
public class SettlementPreviewItem {
    private String invigilatorCode;
    private String invigilatorName;
    private Integer sessionCount;
    private BigDecimal amount;
}
