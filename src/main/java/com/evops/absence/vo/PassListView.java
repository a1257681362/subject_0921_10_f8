package com.evops.absence.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 合格名单：本批次成绩有效（无生效的无效化裁定）的学员名单，按等级汇总。
 */
@Data
public class PassListView {
    private Long batchId;
    private String batchCode;
    private int totalCount;
    /** 各等级人数（如 {"优秀":1,"合格":2}）。 */
    private Map<String, Integer> gradeSummary;
    private List<PassListItem> items;
}
