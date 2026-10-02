package com.evops.absence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 缺考标记模块（主表）：一人一批次一类型一条裁定；当前状态取最新版本，版本链见 t_absence_mark_version。
 * 类型：ABSENT 缺考 / ABANDONED 弃考 / VIOLATION 违纪。
 * 状态：PENDING 待复核 / CONFIRMED 已生效（最终裁定） / REJECTED 已驳回。
 * 结论 score_valid 决定成绩有没有效；无效必填原因与衔接下次安排。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_absence_mark")
public class AbsenceMark extends BaseEntity {
    /** 裁定号 QK + 批次编码 + 4 位流水。 */
    private String markNo;
    private Long batchId;
    private Long registrationId;
    private String regNo;
    private String name;
    private String markType;
    private String source;
    /** 当前版本号，从 1 起；复核/变更各追加一版。 */
    private Integer currentVersion;
    private String status;
    /** 裁定结论：成绩是否有效（待复核时为拟定值，确认后生效）。 */
    private Boolean scoreValid;
    private String invalidReason;
    /** 衔接下次安排（无效必填）。 */
    private String nextArrangement;
    private Long nextBatchId;
    private String reviewer;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    private String operator;
    private String remark;
}
