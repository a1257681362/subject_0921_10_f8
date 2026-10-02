package com.evops.vault.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 保卫异常告警：异常时段进出在登记生效时自动标记生成；保卫签收留痕。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_vault_alert")
public class VaultAlert extends BaseEntity {
    private Long accessId;
    /** OFF_HOURS 异常时段进出。 */
    private String alertType;
    private String title;
    private String content;
    /** NEW 待处理 / ACKED 已签收。 */
    private String status;
    private String ackedBy;
    private LocalDateTime ackedAt;
    private String ackNote;
}
