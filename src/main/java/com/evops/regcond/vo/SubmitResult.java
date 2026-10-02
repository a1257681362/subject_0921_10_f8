package com.evops.regcond.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量提交执行结果：新落库提交单 + 申诉中跳过清单。
 */
@Data
public class SubmitResult {
    private String action = "REG_CONDITION_SUBMIT";
    private String operator;
    private String executedAt;
    private List<SubmitItemView> created = new ArrayList<>();
    private List<SubmitItemView> skipped = new ArrayList<>();
}
