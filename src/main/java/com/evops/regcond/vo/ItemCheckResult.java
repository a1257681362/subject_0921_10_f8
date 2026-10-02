package com.evops.regcond.vo;

import lombok.Data;

/**
 * 单项核验结果（内存态；落库转 t_reg_condition_check）。
 */
@Data
public class ItemCheckResult {
    /** EDUCATION / YEARS / PRIOR_CERT */
    private String itemType;
    private String itemKey;
    private String itemLabel;
    /** PASS / FAIL / DOUBT */
    private String result;
    /** 逐条中文原因。 */
    private String reason;
    /** 机器初判明细 JSON。 */
    private String detailJson;

    public static ItemCheckResult of(String itemType, String itemKey, String itemLabel,
                                     String result, String reason, String detailJson) {
        ItemCheckResult r = new ItemCheckResult();
        r.setItemType(itemType);
        r.setItemKey(itemKey);
        r.setItemLabel(itemLabel);
        r.setItemResult(result, reason);
        r.setDetailJson(detailJson);
        return r;
    }

    public void setItemResult(String result, String reason) {
        this.result = result;
        this.reason = reason;
    }
}
