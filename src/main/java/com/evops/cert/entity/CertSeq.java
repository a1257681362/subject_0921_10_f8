package com.evops.cert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 发号序列：按 规则+年度+批次 维度发流水。
 * current_seq 只增不减，发号时与库内已用最大流水取大值起跳，手工旧号补登后自动抬过旧号，不撞旧号。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_cert_seq")
public class CertSeq extends BaseEntity {
    private Long ruleId;
    private Long ruleVersionId;
    private Integer certYear;
    private String certBatch;
    private Integer currentSeq;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
