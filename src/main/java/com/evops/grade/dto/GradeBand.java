package com.evops.grade.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 分数段：左闭右开 [minScore, maxScore)，末段右端含满分。
 * 段段相连、不重叠、不缺口，首段从 0 起、末段到满分止。
 */
@Data
public class GradeBand {
    private BigDecimal minScore;
    private BigDecimal maxScore;
    /** 该段等级名，如 合格 / 不合格 / A / B。 */
    private String grade;
}
