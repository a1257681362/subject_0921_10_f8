package com.evops.appoint.dto;

import lombok.Data;

/**
 * 月底一键出结算单：指定结算月份（yyyy-MM），缺省当前月。
 * 汇总该月内已签到（CHECKED_IN / billable）且未被确认结算单锁定的聘任。
 */
@Data
public class SettlementGenerateRequest {
    /** 结算月份 yyyy-MM；缺省当前月。 */
    private String month;
    private String remark;
}
