package com.evops.regcond.vo;

import lombok.Data;

import java.util.List;

/**
 * 单人提交预检/执行结果行。
 */
@Data
public class SubmitItemView {
    private String regNo;
    private String name;
    private String levelCode;
    /** 预检是否可落库（申诉中跳过、结构性错误均为 false）。 */
    private boolean actionable;
    /** 是否被申诉中状态跳过（申诉期间不重复拦截）。 */
    private boolean appealInProgress;
    private String appealNo;
    /** 预期/实际提交单状态：PASS / FAIL / MANUAL_PENDING。 */
    private String status;
    private Long submissionId;
    private String submissionNo;
    private String ruleVersionNo;
    private int failCount;
    private int doubtCount;
    private int passCount;
    private List<ItemCheckResult> checks;
    /** 结构性错误（如级别未配置规则）：执行时任一存在整批拒收。 */
    private String errorCode;
    private String note;
}
