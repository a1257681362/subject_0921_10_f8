package com.evops.appoint.dto;

import lombok.Data;

/**
 * 签到：场次结束监考人员签到，计津贴。可显式指定签到钟点（缺省当前时间）。
 */
@Data
public class CheckInRequest {
    /** 签到钟点，缺省当前时间；结算周期按该时间所属月份归属。 */
    private String signInAt;
}
