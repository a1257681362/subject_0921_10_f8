package com.evops.regcond.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 报名条件申诉单：学员对不通过/人工判罚申诉；处理中不允许重复提交该报名，结论只追加留痕。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_reg_appeal")
public class RegAppeal extends BaseEntity {
    private String appealNo;
    private Long submissionId;
    private String projectCode;
    private String levelCode;
    private Long batchId;
    private String regNo;
    private String name;
    private String reason;
    private String appellant;
    private LocalDateTime appliedAt;
    /** PENDING / APPROVED / REJECTED */
    private String status;
    private String conclusion;
    private String reviewer;
    private LocalDateTime reviewedAt;
}
