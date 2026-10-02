package com.evops.absence.vo;

import lombok.Data;

import java.util.List;

/**
 * 自动标缺考预检：先看影响范围，确认后再执行。
 */
@Data
public class ScanPreview {
    private Long batchId;
    private String batchCode;
    private int totalScanned;
    private int actionableCount;
    private int skipCount;
    private String summary;
    private List<ScanPreviewItem> items;
}
