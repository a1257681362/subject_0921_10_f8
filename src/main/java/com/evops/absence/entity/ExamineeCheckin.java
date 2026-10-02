package com.evops.absence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 考生签到：现场签到登记即生效；补录走审批（PENDING→CONFIRMED/REJECTED），审批人须非补录提交人。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_examinee_checkin")
public class ExamineeCheckin extends BaseEntity {
    private Long batchId;
    private Long registrationId;
    private String regNo;
    private String name;
    /** 签到钟点（补录为纸面/实际签到时间）。 */
    private LocalDateTime checkInAt;
    /** LIVE 现场 / SUPPLEMENT 补录。 */
    private String source;
    /** PENDING 待审批 / CONFIRMED 已生效 / REJECTED 已驳回。 */
    private String status;
    /** 补录理由（补录必填）。 */
    private String reason;
    private String reviewer;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    private String operator;
    private String remark;
}
