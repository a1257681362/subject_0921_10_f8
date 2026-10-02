package com.evops.regcond.dto;

import lombok.Data;

import java.util.List;

/**
 * 条件规则版本冻结的核验项配置（一个级别一套）：
 * - education 学历要求（可空表示不核验学历）
 * - years 从业年限要求（可空表示不核验年限）
 * - priorCerts 前置证书要求清单（可空表示不要求前置证书）
 * 发布时整体序列化为 JSON 冻结，规则变更只产生新版本。
 */
@Data
public class RegConditionItemsConfig {
    private EducationItemConfig education;
    private YearsItemConfig years;
    private List<PriorCertItemConfig> priorCerts;
    /** 配置备注（如特殊口径说明），随版本冻结。 */
    private String note;
}
