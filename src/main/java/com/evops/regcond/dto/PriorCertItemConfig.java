package com.evops.regcond.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 前置证书核验项（一条证书要求）：
 * - itemKey 要求标识（学员提交材料按 key 对应），同一版本内唯一；
 * - checkValidity=true 时连有效期一起验：内部证书按 签发日+validYears（未配 validYears 则以证书自带有效期为准），
 *   外部证书按学员填报的签发日/到期日，机器判不准的存疑转人工。
 */
@Data
public class PriorCertItemConfig {
    @NotBlank(message = "前置证书标识不能为空")
    private String itemKey;
    @NotBlank(message = "前置证书名称不能为空")
    private String certName;
    /** 是否必备，缺省 true。 */
    private Boolean required;
    /** 是否核验有效期，缺省 true。 */
    private Boolean checkValidity;
    /** 规定有效期年数；为空表示以证书自带到期日/长期有效为准。 */
    private Integer validYears;
    /** 核验项展示名，缺省取 certName。 */
    private String label;
}
