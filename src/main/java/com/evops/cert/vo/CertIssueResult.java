package com.evops.cert.vo;

import lombok.Data;

import java.util.List;

/**
 * 发证执行结果：实发编号逐条带回；结构性错误整批拒收不落半条，重复持证人逐条跳过并给原因。
 */
@Data
public class CertIssueResult {
    private String projectCode;
    private String ruleVersionNo;
    private Integer certYear;
    private String certBatch;
    private int issuedCount;
    private int skipCount;
    private String summary;
    private List<CertAffectedItem> items;
}
