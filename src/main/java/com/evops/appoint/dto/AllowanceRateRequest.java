package com.evops.appoint.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 监考津贴标准建档：角色不同标准不同，可按项目与生效区间配置。
 */
@Data
public class AllowanceRateRequest {
    @NotBlank(message = "标准编码不能为空")
    private String code;
    @NotBlank(message = "标准名称不能为空")
    private String name;
    /** 适用项目；缺省 DEFAULT 通用。 */
    private String projectCode;
    /** CHIEF / INVIGILATOR / PATROL。 */
    @NotBlank(message = "监考角色不能为空")
    private String roleCode;
    /** 单场津贴（元）。 */
    @NotNull(message = "单场津贴金额不能为空")
    private BigDecimal amount;
    /** 生效起止（闭区间）；为空表示长期有效。 */
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String remark;
}
