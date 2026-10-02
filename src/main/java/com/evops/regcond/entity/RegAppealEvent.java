package com.evops.regcond.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 申诉流转事件：提交申诉/给出结论，只追加。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_reg_appeal_event")
public class RegAppealEvent extends BaseEntity {
    private Long appealId;
    /** APPLY / CONCLUDE */
    private String action;
    private String fromStatus;
    private String toStatus;
    private String detail;
    private String operator;
    private LocalDateTime eventAt;
}
