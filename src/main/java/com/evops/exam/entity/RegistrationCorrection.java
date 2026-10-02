package com.evops.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 名册勘误：只追加。更正时旧值写入此表，不抹掉历史。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_registration_correction")
public class RegistrationCorrection extends BaseEntity {
    private Long registrationId;
    private String fieldName;
    private String fieldLabel;
    private String oldValue;
    private String newValue;
    private String reason;
    private String operator;
    private LocalDateTime correctedAt;
}
