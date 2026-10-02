package com.evops.appoint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 兼职监考人员。本人视图按 code（X-Self-Code）过滤。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_invigilator")
public class Invigilator extends BaseEntity {
    private String code;
    private String name;
    /** CHIEF 主考 / INVIGILATOR 监考 / PATROL 巡考 */
    private String roleCode;
    private String phone;
    /** PART_TIME 兼职 / FULL_TIME 在编（当前仅兼职产生监考费） */
    private String staffKind;
    /** ENABLED / DISABLED */
    private String status;
    private String remark;
}
