package com.evops.vault.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * 进出刷证登记：按卡号或人员编码定位人员；可随登记挂钩卷箱（卷箱出入跟人员进出挂钩）。
 * 登记落库为待复核，第二人复核确认才生效。
 */
@Data
public class SwipeRequest {

    /** 人员编码（与 cardNo 二选一）。 */
    private String personCode;

    /** 刷证卡号（与 personCode 二选一）。 */
    private String cardNo;

    @NotBlank(message = "进出方向不能为空（IN 进 / OUT 出）")
    private String direction;

    /** 刷证发生时间，缺省当前时间；支持 yyyy-MM-ddTHH:mm:ss / yyyy-MM-dd HH:mm:ss / yyyy-MM-dd HH:mm / yyyy-MM-dd。 */
    private String occurredAt;

    /** 随本次进出挂钩的卷箱箱号（可空）。 */
    private List<String> boxes;

    private String remark;
}
