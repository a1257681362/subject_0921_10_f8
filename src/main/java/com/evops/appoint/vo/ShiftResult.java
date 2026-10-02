package com.evops.appoint.vo;

import lombok.Data;

import java.util.List;

/**
 * 排班生成聘任执行结果：新建聘任编号与逐条跳过原因。
 */
@Data
public class ShiftResult {
    private String action;
    private Long batchId;
    private String batchCode;
    private int createdCount;
    private int skipCount;
    private String operator;
    private String executedAt;
    private List<String> appointmentNos;
    private List<String> skipped;
}
