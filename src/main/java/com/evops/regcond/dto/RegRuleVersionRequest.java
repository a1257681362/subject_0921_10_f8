package com.evops.regcond.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * 新建条件规则草稿版本：核验项配置发布时冻结进版本。
 * 三类核验项均可缺省（null/未启用）：学历、年限、前置证书清单。
 */
@Data
public class RegRuleVersionRequest {
    @Valid
    @NotNull(message = "核验项配置不能为空")
    private RegConditionItemsConfig items;
    private String remark;
}
