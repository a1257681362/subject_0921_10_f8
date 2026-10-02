package com.evops.vault.vo;

import lombok.Data;

import java.util.List;

/**
 * 纸面存量导入执行结果。
 */
@Data
public class ImportResult {
    private String action;
    private int importedCount;
    private int skipCount;
    /** 导入后落在异常时段并告警保卫的条数。 */
    private int abnormalCount;
    private String operator;
    private String executedAt;
    private List<String> accessNos;
    private List<String> skipped;
}
