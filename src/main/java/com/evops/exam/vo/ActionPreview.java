package com.evops.exam.vo;

import lombok.Data;

import java.util.List;

/**
 * 批量动作预检：先展示影响范围，确认后再 execute。
 * mix* / seed / sameSchoolAdjacent* 字段仅编排 / 重排预检返回。
 */
@Data
public class ActionPreview {
    private String action;
    private int totalScanned;
    private int actionableCount;
    private int skipCount;
    private String summary;
    private List<AffectedItem> items;

    /** 本次采用的混编算法版本号（无生效版本时为内置默认）。 */
    private String mixVersionNo;
    /** 洗牌随机种子（同种子可复现座次）。 */
    private Long seed;
    /** 使用考场数。 */
    private Integer roomCount;
    /** 同校相邻对数（巡考抽查口径）。 */
    private Integer sameSchoolAdjacentCount;
    /** 同校相邻明细。 */
    private List<AdjacentPairView> adjacentPairs;
}
