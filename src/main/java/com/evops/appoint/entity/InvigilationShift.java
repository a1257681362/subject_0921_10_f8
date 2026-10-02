package com.evops.appoint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 监考排班：编排之后把监考人员排进某批次某考场的某个角色场次。
 * 聘任随排班生成；一个在岗排班对应一条聘任（uk_appointment_shift）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_invigilation_shift")
public class InvigilationShift extends BaseEntity {
    private Long batchId;
    private Long roomId;
    /** CHIEF / INVIGILATOR / PATROL */
    private String roleCode;
    private Long invigilatorId;
    private LocalDateTime shiftStart;
    private LocalDateTime shiftEnd;
    /** SCHEDULED 已排班 / CANCELLED 已取消 */
    private String status;
    private String remark;
}
