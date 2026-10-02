package com.evops.cert.dto;

import lombok.Data;

/**
 * 发号参数（规则版本发布时冻结的 JSON 内容）。
 * 编号形态：prefix + 年度(yearDigits) + 批次(batchDigits) + 流水(seqDigits) + 末位校验码。
 * 例如 ZS + 2026 + 01 + 00001 + X => ZS20260100001X。
 */
@Data
public class CertRuleConfig {
    /** 编号前缀（大写字母/数字，如 ZS）。 */
    private String prefix;
    /** 年度位数：2 或 4，缺省 4。 */
    private Integer yearDigits;
    /** 批次段位数：1~4，缺省 2。 */
    private Integer batchDigits;
    /** 流水段位数：3~8，缺省 5。 */
    private Integer seqDigits;
    /** 末位是否带校验码（ISO 7064 MOD 11-2，10 记为 X），缺省 true。 */
    private Boolean checkEnabled;
}
