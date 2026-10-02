package com.evops.appoint.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 结算调整：结算单确认锁定后唯一的改动入口。变动额可正可负，只追加台账。
 */
@Data
public class SettlementAdjustRequest {
    @NotBlank(message = "被调整监考人员编码不能为空")
    private String invigilatorCode;
    /** 本条变动额（元，可正可负）。 */
    @NotNull(message = "调整金额不能为空")
    private BigDecimal adjustAmount;
    @NotBlank(message = "调整原因不能为空")
    private String reason;
}
