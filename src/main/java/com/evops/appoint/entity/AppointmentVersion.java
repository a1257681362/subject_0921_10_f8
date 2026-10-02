package com.evops.appoint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 聘任记录版本：生成/换人/签到/缺勤/取消都只追加一版，永不修改。
 * 一次换人产生两版：被换下者 REPLACED 版（billable=false），接任者 APPOINTED 版。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_appointment_version")
public class AppointmentVersion extends BaseEntity {
    private Long appointmentId;
    private Integer versionNo;
    /** APPOINTED / REPLACED / CHECKED_IN / ABSENT / CANCELLED */
    private String status;
    private Long invigilatorId;
    private String invigilatorCode;
    private String invigilatorName;
    private String roleCode;
    private Long rateId;
    private String rateCode;
    private BigDecimal allowanceAmount;
    private Boolean billable;
    /** CREATE / REPLACE / REPLACE_NEW / CHECKIN / ABSENT / CANCEL */
    private String changeType;
    private String changeReason;
    private Long replacementId;
    private LocalDateTime signInAt;
    private String operator;
    private LocalDateTime changedAt;
}
