package com.evops.grade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成绩：一人一批次一条；定级时记下规则版本，规则升级不影响历史口径。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_exam_score")
public class ExamScore extends BaseEntity {
    private Long batchId;
    private Long registrationId;
    private String regNo;
    private String name;
    /** 卷面分（复核改分直接更正卷面分，改分前后进日志）。 */
    private BigDecimal paperScore;
    /** 最终分：本版暂不启用（留作加分/合成），与卷面分一致留空。 */
    private BigDecimal finalScore;
    private String grade;
    private Long ruleVersionId;
    private String ruleVersionNo;
    private LocalDateTime gradedAt;
    /** RECORDED 已录入 / REVIEWING 复核中 */
    private String status;
    /** 成绩是否有效：缺考裁定生效时由裁定模块回写；统计与合格名单只认有效成绩。 */
    private Boolean valid;
    private String remark;
}
