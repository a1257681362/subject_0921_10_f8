package com.evops.appoint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 换人替换记录：一次换人一条，记录谁换下谁、谁接任、原因与经办。
 * 与聘任版本链互相印证（被换下者 REPLACED 版、接任者 APPOINTED 版指向同一条记录）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_appointment_replacement")
public class AppointmentReplacement extends BaseEntity {
    private Long appointmentId;
    private Long batchId;
    private Long fromInvigilatorId;
    private String fromCode;
    private String fromName;
    private Long toInvigilatorId;
    private String toCode;
    private String toName;
    private String roleCode;
    private String reason;
    private String operator;
    private LocalDateTime replacedAt;
}
