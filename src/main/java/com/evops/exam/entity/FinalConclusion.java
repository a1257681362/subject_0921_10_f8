package com.evops.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 最终结论：每个批次一条，与阶段结果物理分开。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_final_conclusion")
public class FinalConclusion extends BaseEntity {
    private Long batchId;
    private String conclusionCode;
    private String summary;
    private Integer totalCount;
    private Integer printedCount;
    private Integer issuedCount;
    private String operator;
    private LocalDateTime concludedAt;
}
