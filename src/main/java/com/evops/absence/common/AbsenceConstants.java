package com.evops.absence.common;

/**
 * 缺考裁定与成绩快照业务常量。
 */
public final class AbsenceConstants {

    private AbsenceConstants() {
    }

    // 签到来源
    /** 现场签到：登记即生效。 */
    public static final String CHECKIN_LIVE = "LIVE";
    /** 补录：走审批，批准才生效。 */
    public static final String CHECKIN_SUPPLEMENT = "SUPPLEMENT";

    // 签到状态
    /** 待审批（仅补录）。 */
    public static final String CHECKIN_PENDING = "PENDING";
    /** 已生效。 */
    public static final String CHECKIN_CONFIRMED = "CONFIRMED";
    /** 已驳回（永不生效）。 */
    public static final String CHECKIN_REJECTED = "REJECTED";

    // 违纪类型
    public static final String VIOLATION_CHEAT = "CHEAT";
    public static final String VIOLATION_IMPERSONATE = "IMPERSONATE";
    public static final String VIOLATION_PROHIBITED = "PROHIBITED";
    public static final String VIOLATION_DISORDER = "DISORDER";
    public static final String VIOLATION_OTHER = "OTHER";

    // 缺考标记类型（缺考与弃考分清）
    /** 缺考：未到场（无生效签到）。 */
    public static final String TYPE_ABSENT = "ABSENT";
    /** 弃考：主动放弃（手动标记）。 */
    public static final String TYPE_ABANDONED = "ABANDONED";
    /** 违纪：凭违纪记录标记。 */
    public static final String TYPE_VIOLATION = "VIOLATION";

    // 标记来源
    /** 签到缺失自动标记。 */
    public static final String SRC_AUTO_CHECKIN = "AUTO_CHECKIN";
    /** 违纪记录自动标记。 */
    public static final String SRC_AUTO_VIOLATION = "AUTO_VIOLATION";
    /** 手动标记。 */
    public static final String SRC_MANUAL = "MANUAL";
    /** 补录审批通过触发的变更。 */
    public static final String SRC_RECHECK = "RECHECK";

    // 标记状态（状态机）
    /** 待复核：新标记 / 变更申请，未生效、不计入统计。 */
    public static final String MARK_PENDING = "PENDING";
    /** 已生效：双人复核确认的最终裁定，统计只认它。 */
    public static final String MARK_CONFIRMED = "CONFIRMED";
    /** 已驳回：复核驳回且此前无生效裁定，永不生效。 */
    public static final String MARK_REJECTED = "REJECTED";

    // 版本变更类型
    /** 标记（新建 / 变更申请落的第一版）。 */
    public static final String CHANGE_CREATE = "CREATE";
    /** 复核确认。 */
    public static final String CHANGE_CONFIRM = "CONFIRM";
    /** 复核驳回。 */
    public static final String CHANGE_REJECT = "REJECT";
    /** 变更申请（已生效裁定发起变更，回到待复核）。 */
    public static final String CHANGE_CHANGE = "CHANGE";
}
