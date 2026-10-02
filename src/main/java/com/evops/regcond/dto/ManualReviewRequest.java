package com.evops.regcond.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 存疑转人工复核：给出整体结论；可对个别存疑项分别改判（未列出的存疑项随整体结论）。
 */
@Data
public class ManualReviewRequest {
    @NotNull(message = "人工结论不能为空（PASS/FAIL）")
    private String outcome;
    private String comment;
    private List<ManualItemResult> itemResults;

    @Data
    public static class ManualItemResult {
        private Long checkId;
        private String result;
        private String reason;
    }
}
