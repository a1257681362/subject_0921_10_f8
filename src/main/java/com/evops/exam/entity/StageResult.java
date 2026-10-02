package com.evops.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 阶段结果：与最终结论分表保存。
 * stage: IMPORT 报名导入 / ARRANGEMENT 编排 / PRINTING 打印 / ISSUANCE 发放
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_stage_result")
public class StageResult extends BaseEntity {
    private Long batchId;
    private String stage;
    private Integer affectedCount;
    private String detail;
    private String operator;
    private LocalDateTime executedAt;
}
