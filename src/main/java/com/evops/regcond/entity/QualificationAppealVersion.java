package com.evops.regcond.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 申诉流转版本：CREATE/ACCEPT/APPROVE/REJECT 只追加，流转全程留痕。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_qualification_appeal_version")
public class QualificationAppealVersion extends BaseEntity {
    private Long appealId;
    private Integer versionNo;
    /** SUBMITTED / REVIEWING / APPROVED / REJECTED。 */
    private String status;
    /** CREATE / ACCEPT / APPROVE / REJECT。 */
    private String changeType;
    private String changeReason;
    private String reviewer;
    private String operator;
    private LocalDateTime changedAt;
}
