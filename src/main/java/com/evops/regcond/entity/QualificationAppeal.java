package com.evops.regcond.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 条件核验申诉单：学员对不通过 / 存疑结论的某个核验项申诉；流转版本只追加。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_qualification_appeal")
public class QualificationAppeal extends BaseEntity {
    /** 申诉单号 SS + yyMMdd + 4 位流水。 */
    private String appealNo;
    private Long qualificationId;
    private Long checkItemId;
    private String projectCode;
    private String levelCode;
    /** 人员键：证件号优先，缺则报名号；豁免按 人+项目+级别+核验项 维度生效。 */
    private String personKey;
    private String itemKey;
    private String itemType;
    private String itemName;
    private String applicant;
    private String applicantName;
    private String reason;
    private String evidence;
    /** SUBMITTED 待受理 / REVIEWING 处理中 / APPROVED 通过（该项豁免） / REJECTED 驳回。 */
    private String status;
    private Integer currentVersion;
    private String reviewer;
    private String conclusion;
    private LocalDateTime concludedAt;
    private String operator;
    private LocalDateTime appliedAt;
}
