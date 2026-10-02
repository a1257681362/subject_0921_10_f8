package com.evops.mixalgo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 混编算法版本：参数 JSON 在建草稿时冻结；发布后永不修改。
 * DRAFT → ACTIVE → DISABLED；同一算法同时刻仅一版 ACTIVE。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_mix_algorithm_version")
public class MixAlgorithmVersion extends BaseEntity {
    private Long algorithmId;
    private String versionNo;
    /** DRAFT / ACTIVE / DISABLED */
    private String status;
    /** 发布时冻结的算法参数 JSON */
    private String params;
    private String remark;
    private String publishedBy;
    private LocalDateTime publishedAt;
    private String disabledBy;
    private LocalDateTime disabledAt;
    private String operator;
}
