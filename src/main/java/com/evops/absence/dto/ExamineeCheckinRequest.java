package com.evops.absence.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 现场签到：登记即生效（CONFIRMED），签到人数进入统计。
 */
@Data
public class ExamineeCheckinRequest {

    @NotNull(message = "批次不能为空")
    private Long batchId;

    @NotBlank(message = "报名号不能为空")
    private String regNo;

    /** 签到钟点，缺省当前时间；支持 yyyy-MM-dd HH:mm(:ss) 等格式。 */
    private String checkInAt;

    private String remark;
}
