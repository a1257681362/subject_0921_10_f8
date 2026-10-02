package com.evops.regcond.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 逐项核验结果：一个核验项一行；不通过逐条给原因，存疑项由人工改判。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_reg_condition_check")
public class RegConditionCheck extends BaseEntity {
    private Long submissionId;
    /** EDUCATION / YEARS / PRIOR_CERT */
    private String itemType;
    /** 项内标识：学历/年限固定为自身类型名；前置证书为要求标识（证书类别/名称）。 */
    private String itemKey;
    private String itemLabel;
    /** PASS / FAIL / DOUBT */
    private String result;
    /** 逐条中文原因。 */
    private String reason;
    /** 人工改判结果：PASS / FAIL。 */
    private String manualResult;
    private String manualReason;
    private String reviewer;
    private LocalDateTime reviewedAt;
    /** 机器初判明细 JSON（实际值 vs 要求值、证书状态等）。 */
    private String detailJson;
}
