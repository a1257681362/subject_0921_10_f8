package com.evops.vault.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 出入登记主表（模块）：一次刷证一条。内容（人/方向/钟点/卷箱）写入后不改，纠错走补充说明；
 * 状态机 PENDING 待复核 → CONFIRMED 已生效 / REJECTED 已驳回，每次流转只追加版本（见 t_vault_access_version）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_vault_access")
public class VaultAccess extends BaseEntity {
    /** 登记号 VA + 发生日期 8 位 + 4 位流水。 */
    private String accessNo;
    private Long personId;
    private String personCode;
    private String personName;
    private String cardNo;
    /** IN 进室 / OUT 出室。 */
    private String direction;
    /** 刷证发生时间（纸面导入为纸面记录时间）。 */
    private LocalDateTime occurredAt;
    /** LIVE 现场刷证 / PAPER 纸面存量导入。 */
    private String source;
    /** 当前版本状态：PENDING / CONFIRMED / REJECTED。 */
    private String status;
    private Integer currentVersion;
    /** 异常时段标记：发生时间落在允许窗口外。 */
    private Boolean abnormal;
    private String abnormalReason;
    private String reviewerCode;
    private String reviewerName;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    /** 随本次登记挂钩的卷箱条数（冗余便于列表）。 */
    private Integer boxCount;
    private String operator;
    private String remark;
}
