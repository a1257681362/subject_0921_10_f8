package com.evops.exam.vo;

import lombok.Data;

import java.util.List;

/**
 * 批量动作执行结果：成功与跳过分开报，跳过附原因。
 * mix* / seed / sameSchoolAdjacent* 字段仅编排 / 重排返回。
 */
@Data
public class ActionResult {
    private String action;
    private int affectedCount;
    private int skipCount;
    private String operator;
    private String executedAt;
    private List<String> affectedKeys;
    private List<String> skipped;

    /** 编排记录 id（种子与座次落库的 t_arrangement_run 主键）。 */
    private Long arrangementRunId;
    private String mixVersionNo;
    private Long seed;
    private Integer roomCount;
    private Integer sameSchoolAdjacentCount;
    private List<AdjacentPairView> adjacentPairs;
}
