package com.evops.grade.vo;

import lombok.Data;

import java.util.List;

/**
 * 成绩批量录入预检：定级所用规则版本一并回显，两个岗口径统一。
 */
@Data
public class ScoreImportPreview {
    private Long batchId;
    private String batchCode;
    private String projectCode;
    private String ruleVersionNo;
    private int totalScanned;
    private int actionableCount;
    private int skipCount;
    private String summary;
    private List<ScorePreviewItem> items;
}
