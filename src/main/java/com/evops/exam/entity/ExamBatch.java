package com.evops.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_exam_batch")
public class ExamBatch extends BaseEntity {
    private String code;
    private String name;
    private Long siteId;
    private String subject;
    /** 项目编码：等级规则与复核窗口按项目串起来。 */
    private String projectCode;
    private LocalDate examDate;
    private LocalTime startTime;
    private LocalTime endTime;
    /** 成绩复核窗口起止：跟批次配置走；为空表示未配置窗口。 */
    private LocalDateTime reviewStartAt;
    private LocalDateTime reviewEndAt;
    /** CREATED 建档 / ARRANGED 已编排 / FINALIZED 已办结 */
    private String status;
    private String remark;
}
