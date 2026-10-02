package com.evops.regcond.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * 从业年限核验项：minYears 为最低周年数（算头算尾：起算日含头、提交日含尾，按对日算周年）。
 * 起算口径取规则主表 yearsBasis：工作起始日 或 前一证书取得日。
 */
@Data
public class YearsItemConfig {
    /** 是否启用（false/null 等价不核验）。 */
    private Boolean enabled;
    @NotNull(message = "最低年限不能为空")
    @Min(value = 0, message = "最低年限不能为负")
    private Integer minYears;
    /** 核验项展示名，缺省"从业年限"。 */
    private String label;
}
