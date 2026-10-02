package com.evops.absence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 缺考标记版本：标记 / 复核确认 / 驳回 / 变更各追加一版，永不修改。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_absence_mark_version")
public class AbsenceMarkVersion extends BaseEntity {
    private Long markId;
    private Integer versionNo;
    /** 本版状态：PENDING / CONFIRMED / REJECTED。 */
    private String status;
    private String markType;
    /** 本版结论快照。 */
    private Boolean scoreValid;
    private String invalidReason;
    private String nextArrangement;
    private Long nextBatchId;
    /** CREATE 标记 / CONFIRM 复核确认 / REJECT 复核驳回 / CHANGE 变更申请。 */
    private String changeType;
    private String changeReason;
    private String reviewer;
    private String operator;
    private LocalDateTime changedAt;
}
