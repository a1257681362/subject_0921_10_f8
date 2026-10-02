package com.evops.absence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 批次缺考统计：每批次一行当前值；统计只认最终裁定（CONFIRMED 标记），
 * 裁定有变更就重算（generation 递增），过程由标记版本链留痕。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_absence_stat")
public class AbsenceStat extends BaseEntity {
    private Long batchId;
    /** 重算代次，每次重算 +1。 */
    private Integer generation;
    private Integer totalRegistrations;
    private Integer checkedInCount;
    private Integer supplementPendingCount;
    private Integer markPendingCount;
    /** 已生效裁定分类型计数（缺考/弃考/违纪分清）。 */
    private Integer absentCount;
    private Integer abandonedCount;
    private Integer violationCount;
    private Integer scoreValidCount;
    private Integer scoreInvalidCount;
    private Integer passListCount;
    /** 有效成绩按等级分布 JSON。 */
    private String gradeSummary;
    private String triggerReason;
    private String operator;
    private LocalDateTime computedAt;
}
