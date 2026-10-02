package com.evops.cert.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 批量发证请求：同一 规则版本+年度+批次 下连续发流水。
 * 两步：先 /issue/preview 看可发/跳过，确认后再 /issue。
 */
@Data
public class CertIssueRequest {
    /** 项目编码；为空按 DEFAULT（助考等项目传 ZHUKAO）。 */
    private String projectCode;
    /** 关联考试批次（可选）：用于回填批次编码/名称、校验项目归属。 */
    private Long batchId;
    /** 证书年度；缺省当前年。 */
    private Integer certYear;
    /** 证书批次段（纯数字，位数须不超规则 batchDigits）；batchId 给出且此项为空时用批次编码数字部分。 */
    private String certBatch;
    @Valid
    @NotEmpty(message = "发证清单 items 不能为空")
    private List<CertIssueItem> items;
    private String remark;
}
