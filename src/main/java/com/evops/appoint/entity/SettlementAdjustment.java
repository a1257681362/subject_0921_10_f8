package com.evops.appoint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 结算调整台账：结算单确认锁定后唯一的改动入口，只追加。
 * 每条变动额（可正可负）累加到结算单与本人明细的应付上。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_settlement_adjustment")
public class SettlementAdjustment extends BaseEntity {
    private Long settlementId;
    private Long invigilatorId;
    private String invigilatorCode;
    private String invigilatorName;
    /** 本条变动额（可正可负）。 */
    private BigDecimal adjustAmount;
    private String reason;
    private String operator;
    private LocalDateTime adjustedAt;
}
