package com.evops.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 编排记录：每次编排 / 整批重排生成一条（只追加）。
 * 保存本次混编算法版本、随机种子、座次快照与同校相邻统计；同批次仅一条 is_current。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_arrangement_run")
public class ArrangementRun extends BaseEntity {
    private Long batchId;
    /** ARRANGE 首次编排 / REARRANGE 整批重排 */
    private String runType;
    private Long mixVersionId;
    private String mixVersionNo;
    private Long seed;
    /** 座次快照 JSON */
    private String seating;
    private Integer totalCount;
    private Integer roomCount;
    /** 同校相邻对数 */
    private Integer sameSchoolAdjacentCount;
    /** 同校相邻明细 JSON */
    private String adjacentDetail;
    private Boolean isCurrent;
    private String operator;
    private LocalDateTime executedAt;
}
