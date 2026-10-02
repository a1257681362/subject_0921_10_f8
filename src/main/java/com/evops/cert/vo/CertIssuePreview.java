package com.evops.cert.vo;

import lombok.Data;

import java.util.List;

/**
 * 发证预检结果：先看影响范围与拟发编号区间，确认后再执行。
 */
@Data
public class CertIssuePreview {
    private String action;
    private String projectCode;
    private String ruleCode;
    private String ruleVersionNo;
    private Integer certYear;
    private String certBatch;
    /** 该年度批次当前下一可用流水。 */
    private Integer nextSeq;
    private int totalScanned;
    private int actionableCount;
    private int skipCount;
    private String summary;
    private List<CertAffectedItem> items;
}
