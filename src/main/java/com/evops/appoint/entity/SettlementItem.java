package com.evops.appoint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 结算单明细：一张结算单内一人一行（场次合计、金额合计、调整后应付）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_settlement_item")
public class SettlementItem extends BaseEntity {
    private Long settlementId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Long invigilatorId;
    private String invigilatorCode;
    private String invigilatorName;
    private Integer sessionCount;
    private BigDecimal amount;
    /** 调整后应付（初值=金额）。 */
    private BigDecimal payableAmount;
    private String remark;
}
