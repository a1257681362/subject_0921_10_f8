package com.evops.regcond.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报名条件核验单：学员提交即按提交时点的生效规则版本逐项核验；逐项结果见 t_qualification_check_item。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_registration_qualification")
public class RegistrationQualification extends BaseEntity {
    /** 核验单号 QS + yyMMdd + 4 位流水。 */
    private String qualificationNo;
    private Long batchId;
    private String regNo;
    private String name;
    private String idType;
    private String idNo;
    private String phone;
    private String subject;
    private String projectCode;
    /** 报名级别（决定命中哪套条件规则）。 */
    private String levelCode;

    // ---- 提交材料快照 ----
    /** 申报学历（学历等级编码）。 */
    private String education;
    /** 从业起始日期（年限算头算尾的起点）。 */
    private LocalDate workStartDate;
    /** 前置证书编号（学员填报，按编号+姓名核验）。 */
    private String prereqCertNo;

    // ---- 提交时点命中的规则版本 ----
    private Long ruleId;
    private Long ruleVersionId;
    private String ruleVersionNo;

    /** QUALIFIED 合格 / REJECTED 不通过 / MANUAL 存疑转人工。 */
    private String status;
    /** AUTO 自动核验通过 / MANUAL_REVIEW 人工放行 / APPEAL 申诉放行。 */
    private String qualifiedVia;
    private Integer passCount;
    private Integer failCount;
    private Integer manualCount;
    /** 是否存在申诉处理中、暂不拦截的核验项。 */
    private Boolean hasAppealingItem;
    /** 申诉通过放行的依据申诉单。 */
    private Long waiverAppealId;
    /** 原始提交报文 JSON。 */
    private String submitSnapshot;

    // ---- 存疑人工复核 ----
    private String reviewer;
    private LocalDateTime reviewedAt;
    private String reviewComment;

    // ---- 合格后回写报名名册 ----
    private Long registrationId;
    private LocalDateTime enrolledAt;

    private String remark;
    private String operator;
    private LocalDateTime submittedAt;
}
