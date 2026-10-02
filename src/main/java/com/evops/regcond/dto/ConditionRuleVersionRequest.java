package com.evops.regcond.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 条件规则草稿版本：提交核验项列表，发布时冻结。
 */
@Data
public class ConditionRuleVersionRequest {
    @NotEmpty(message = "核验项列表不能为空")
    @Valid
    private List<ConditionItem> items;
    private String remark;
}
