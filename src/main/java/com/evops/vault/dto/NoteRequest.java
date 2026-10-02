package com.evops.vault.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 补充说明（纠错）：登记写入后不改，纠错一律走补充说明，只追加。
 */
@Data
public class NoteRequest {

    /** SUPPLEMENT 补充说明 / CORRECTION 纠错说明；缺省 SUPPLEMENT。 */
    private String noteType;

    @NotBlank(message = "说明内容不能为空")
    private String content;
}
