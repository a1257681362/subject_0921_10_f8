package com.evops.regcond.common;

/**
 * 报名条件规则引擎业务常量。
 */
public final class RegCondConstants {

    private RegCondConstants() {
    }

    /** 默认项目编码（规则未指定项目时）。 */
    public static final String PROJECT_DEFAULT = "DEFAULT";

    // 规则版本状态：与等级规则 / 混编算法 / 证书编号同一套三段式
    public static final String VERSION_DRAFT = "DRAFT";
    public static final String VERSION_ACTIVE = "ACTIVE";
    public static final String VERSION_DISABLED = "DISABLED";

    // 核验项类型
    /** 学历。 */
    public static final String ITEM_EDUCATION = "EDUCATION";
    /** 从业年限（算头算尾）。 */
    public static final String ITEM_YEARS = "YEARS";
    /** 前置证书（连有效期一起验）。 */
    public static final String ITEM_PREREQ_CERT = "PREREQ_CERT";

    // 核验结论
    /** 通过。 */
    public static final String CHECK_PASS = "PASS";
    /** 不通过（硬不符，逐条给原因）。 */
    public static final String CHECK_FAIL = "FAIL";
    /** 存疑：机器判不准，转人工。 */
    public static final String CHECK_MANUAL = "MANUAL";
    /** 申诉处理中，暂不拦截（终裁后回到 PASS 或原结论）。 */
    public static final String CHECK_APPEALING = "APPEALING";

    // 核验单状态
    /** 合格：全部核验项通过（含申诉/人工放行）。 */
    public static final String QUAL_QUALIFIED = "QUALIFIED";
    /** 不通过：存在硬不符项。 */
    public static final String QUAL_REJECTED = "REJECTED";
    /** 存疑：无硬不符但有项需人工核验。 */
    public static final String QUAL_MANUAL = "MANUAL";

    // 合格途径
    public static final String VIA_AUTO = "AUTO";
    public static final String VIA_MANUAL_REVIEW = "MANUAL_REVIEW";
    public static final String VIA_APPEAL = "APPEAL";

    // 申诉状态
    public static final String APPEAL_SUBMITTED = "SUBMITTED";
    public static final String APPEAL_REVIEWING = "REVIEWING";
    public static final String APPEAL_APPROVED = "APPROVED";
    public static final String APPEAL_REJECTED = "REJECTED";

    // 申诉流转类型
    public static final String CHANGE_CREATE = "CREATE";
    public static final String CHANGE_ACCEPT = "ACCEPT";
    public static final String CHANGE_APPROVE = "APPROVE";
    public static final String CHANGE_REJECT = "REJECT";

    // 年限单位
    public static final String UNIT_YEAR = "YEAR";
    public static final String UNIT_MONTH = "MONTH";
}
