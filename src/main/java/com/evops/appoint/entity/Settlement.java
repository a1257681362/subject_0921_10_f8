package com.evops.appoint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 监考费结算单：按周期（月）一键生成。
 * DRAFT 草稿可重复生成/重算；CONFIRMED 已确认锁定，要改走 t_settlement_adjustment。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_settlement")
public class Settlement extends BaseEntity {
    private String settlementNo;
    /** 周期起（含）。 */
    private LocalDate periodStart;
    /** 周期止（不含）。 */
    private LocalDate periodEnd;
    /** 如 2026-09。 */
    private String periodLabel;
    /** DRAFT / CONFIRMED */
    private String status;
    private Integer totalAppointments;
    private BigDecimal totalAmount;
    /** 调整后应付（初值=合计，调整单累加）。 */
    private BigDecimal payableAmount;
    private Integer adjustCount;
    private String remark;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    private String operator;
}
