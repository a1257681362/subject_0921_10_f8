package com.evops.regcond.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 学员申诉：针对某条核验项结论（不通过 / 存疑）。可按 checkItemId 或 核验单+itemKey 定位。
 */
@Data
public class AppealRequest {
    private Long checkItemId;
    private Long qualificationId;
    /** 与 checkItemId 二选一。 */
    private String itemKey;
    @NotBlank(message = "申诉理由不能为空")
    private String reason;
    /** 佐证材料说明（本版文本留档）。 */
    private String evidence;
    /** 申诉人（学员标识；缺省取 X-Operator）。 */
    private String applicant;
}
