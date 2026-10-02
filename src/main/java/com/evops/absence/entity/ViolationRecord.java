package com.evops.absence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 违纪记录：直接登记，作为自动标缺考（VIOLATION 类型）的输入。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_violation_record")
public class ViolationRecord extends BaseEntity {
    private Long batchId;
    private Long registrationId;
    private String regNo;
    private String name;
    /** CHEAT 作弊 / IMPERSONATE 替考 / PROHIBITED 携带违禁品 / DISORDER 扰乱秩序 / OTHER 其他。 */
    private String violationType;
    private String description;
    private LocalDateTime occurredAt;
    private String operator;
}
