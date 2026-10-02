package com.evops.regcond.vo;

import lombok.Data;

import java.util.List;

/**
 * 批量提交预检：逐人列可否受理、机器结论与逐项原因；执行时按同一口径重算。
 */
@Data
public class SubmitPreview {
    private String action = "REG_CONDITION_SUBMIT";
    private int totalScanned;
    private int actionableCount;
    /** 机器判定不通过人数。 */
    private int blockedCount;
    /** 存疑转人工人数。 */
    private int manualPendingCount;
    /** 通过人数。 */
    private int passCount;
    /** 申诉处理中跳过、不重复拦截人数。 */
    private int appealSkipCount;
    /** 结构性问题人数（级别无规则等，执行整批拒收）。 */
    private int errorCount;
    private String summary;
    private List<SubmitItemView> items;
}
