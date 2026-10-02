package com.evops.regcond.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 核验项逐项结果：不通过逐条给原因；申诉中 / 豁免放行都在本行留痕（重新核验生成新单，不覆盖旧行）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_qualification_check_item")
public class QualificationCheckItem extends BaseEntity {
    private Long qualificationId;
    /** 稳定核验项键：类型[:前置证书科目]；豁免按 人+项目+级别+item_key 生效。 */
    private String itemKey;
    /** EDUCATION / YEARS / PREREQ_CERT。 */
    private String itemType;
    private String itemName;
    /** 规则要求（人话）。 */
    private String expected;
    /** 实际核验到的情况（人话）。 */
    private String actual;
    /** PASS / FAIL / MANUAL / APPEALING。 */
    private String result;
    /** 进入申诉前的机器原始结论（FAIL/MANUAL）；申诉驳回时据此恢复拦截。 */
    private String originalResult;
    /** 不通过 / 存疑的逐条原因。 */
    private String reason;
    /** 申诉中放行时关联的在办申诉单。 */
    private Long appealId;
    /** 依据已通过申诉豁免时关联的申诉单。 */
    private Long waiverAppealId;
    private String ruleVersionNo;
}
