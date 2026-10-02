package com.evops.vault.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 补充说明：登记写入后不改，纠错一律走补充说明；只追加，不覆盖历史。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_vault_access_note")
public class VaultAccessNote extends BaseEntity {
    private Long accessId;
    /** SUPPLEMENT 补充说明 / CORRECTION 纠错说明。 */
    private String noteType;
    private String content;
    private String operator;
    private LocalDateTime notedAt;
}
