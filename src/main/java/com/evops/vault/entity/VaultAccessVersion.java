package com.evops.vault.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 出入登记版本：登记 / 复核确认 / 驳回 / 纸面导入各追加一版，永不修改；主表只切当前版本号与状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_vault_access_version")
public class VaultAccessVersion extends BaseEntity {
    private Long accessId;
    private Integer versionNo;
    /** 本版状态：PENDING / CONFIRMED / REJECTED。 */
    private String status;
    /** CREATE 刷证登记 / CONFIRM 复核确认 / REJECT 复核驳回 / IMPORT 纸面存量导入。 */
    private String changeType;
    private String changeReason;
    private String reviewerCode;
    private String reviewerName;
    private String operator;
    private LocalDateTime changedAt;
}
