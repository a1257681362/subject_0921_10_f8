package com.evops.regcond.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 单个核验项（发布时冻结进版本 JSON）：
 * - EDUCATION 学历：requiredEducation 为最低学历等级编码；
 * - YEARS 从业年限：minValue + unit（YEAR/MONTH），年限算头算尾；
 * - PREREQ_CERT 前置证书：requiredSubject 必填、requiredGrade 可空（不要求等级），
 *   checkValidity=true 时连有效期一起验，validityRequired=true 时证书必须登记有效期、否则存疑转人工。
 */
@Data
public class ConditionItem {
    /** EDUCATION / YEARS / PREREQ_CERT。 */
    private String itemType;
    /** 核验项显示名（如「学历」「从业年限」「前置证书（助考一级）」）。 */
    private String name;

    // ---- EDUCATION ----
    /** 最低学历编码：NONE/PRIMARY/JUNIOR_HIGH/SENIOR_HIGH/JUNIOR_COLLEGE/BACHELOR/MASTER/DOCTOR。 */
    private String requiredEducation;

    // ---- YEARS ----
    /** 最低年限（与 unit 配套，>=0）。 */
    private BigDecimal minValue;
    /** YEAR 周年 / MONTH 月；缺省 YEAR。 */
    private String unit;

    // ---- PREREQ_CERT ----
    /** 前置证书要求的科目（证书 subject 必须与之相等）。 */
    private String requiredSubject;
    /** 前置证书要求的等级；为空只看科目。 */
    private String requiredGrade;
    /** 是否校验证书有效期（缺省 true）：有效期覆盖提交日才算有效。 */
    private Boolean checkValidity;
    /** 有效期是否必须已登记：true 时证书未登记有效期（长期有效除外）转人工存疑。 */
    private Boolean validityRequired;
}
