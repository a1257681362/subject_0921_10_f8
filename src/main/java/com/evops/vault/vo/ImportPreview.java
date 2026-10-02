package com.evops.vault.vo;

import lombok.Data;

import java.util.List;

/**
 * 纸面存量导入预检：先展示影响范围（可导入 / 跳过逐条列出），确认后再执行。
 */
@Data
public class ImportPreview {
    private String action;
    private int totalScanned;
    private int actionableCount;
    private int skipCount;
    private String summary;
    private List<ImportPreviewItem> items;
}
