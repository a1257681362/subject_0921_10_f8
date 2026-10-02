package com.evops.regcond.engine;

import lombok.Data;

/**
 * 单个核验项的机器核验结论（不含申诉/豁免分层）。
 */
@Data
public class CheckOutcome {
    /** PASS / FAIL / MANUAL。 */
    private String result;
    /** 规则要求（人话）。 */
    private String expected;
    /** 实际核验到的情况（人话）。 */
    private String actual;
    /** FAIL / MANUAL 的逐条原因。 */
    private String reason;

    public static CheckOutcome pass(String expected, String actual) {
        CheckOutcome o = new CheckOutcome();
        o.result = com.evops.regcond.common.RegCondConstants.CHECK_PASS;
        o.expected = expected;
        o.actual = actual;
        return o;
    }

    public static CheckOutcome fail(String expected, String actual, String reason) {
        CheckOutcome o = new CheckOutcome();
        o.result = com.evops.regcond.common.RegCondConstants.CHECK_FAIL;
        o.expected = expected;
        o.actual = actual;
        o.reason = reason;
        return o;
    }

    public static CheckOutcome manual(String expected, String actual, String reason) {
        CheckOutcome o = new CheckOutcome();
        o.result = com.evops.regcond.common.RegCondConstants.CHECK_MANUAL;
        o.expected = expected;
        o.actual = actual;
        o.reason = reason;
        return o;
    }
}
