package com.evops.appoint.vo;

import lombok.Data;

import java.util.List;

/**
 * 排班生成聘任预检：先展示每条排班的人员、角色、津贴标准与可生成/跳过原因，不落库。
 */
@Data
public class ShiftPreview {
    private String action;
    private Long batchId;
    private String batchCode;
    private int totalScanned;
    private int actionableCount;
    private int skipCount;
    private String summary;
    private List<ShiftPreviewItem> items;
}
