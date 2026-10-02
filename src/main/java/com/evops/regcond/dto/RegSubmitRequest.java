package com.evops.regcond.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 报名条件批量提交：先 preview 看逐人逐项结果与申诉中跳过情况，确认后 execute 落库。
 */
@Data
public class RegSubmitRequest {
    @Valid
    @NotEmpty(message = "报名提交清单不能为空")
    private List<RegSubmitItem> submissions;
}
