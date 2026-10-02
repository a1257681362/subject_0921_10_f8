package com.evops.appoint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 聘任记录主表：一个在岗场次一条。金额/状态取当前版本；版本链见 t_appointment_version。
 * 状态：APPOINTED 已聘任 / REPLACED 已换人 / CHECKED_IN 已签到 / ABSENT 缺勤 / CANCELLED 已取消。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_appointment")
public class Appointment extends BaseEntity {
    private String appointmentNo;
    private Long batchId;
    private Long roomId;
    private Long shiftId;
    private String projectCode;
    private Long invigilatorId;
    private String invigilatorCode;
    private String invigilatorName;
    private String roleCode;
    /** 当前版本号，从 1 起；换人/签到/缺勤/取消各追加一版。 */
    private Integer currentVersion;
    /** 当前版本状态。 */
    private String status;
    private Long rateId;
    private String rateCode;
    /** 当前版本冻结的单场津贴。 */
    private BigDecimal allowanceAmount;
    /** 仅 CHECKED_IN 计费；缺勤/取消/被换下不计费。 */
    private Boolean billable;
    /** 签到钟点（结算周期按签到月归属）。 */
    private LocalDateTime signInAt;
    /** 已进入已确认结算单后置 true 锁定。 */
    private Boolean settled;
    private Long settlementId;
    private String remark;
    private String operator;
}
