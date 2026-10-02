package com.evops.grade.dto;

import lombok.Data;

import java.math.BigDecimal;
import javax.validation.constraints.NotNull;

/**
 * 复核结论：
 * - approved=true 必须给 newScore（与原分不同才叫改分），按原定级版本重算等级；
 * - approved=false 维持原分，结论说明必填。
 */
@Data
public class ReviewConclusionRequest {
    @NotNull(message = "复核结论不能为空（true 通过 / false 驳回）")
    private Boolean approved;
    private BigDecimal newScore;
    private String conclusion;
}
