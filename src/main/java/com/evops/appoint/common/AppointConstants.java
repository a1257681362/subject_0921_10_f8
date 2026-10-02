package com.evops.appoint.common;

/**
 * 聘任与结算业务常量：角色、聘任版本状态、版本变更类型、结算单状态、人员类别。
 */
public final class AppointConstants {

    private AppointConstants() {
    }

    // 监考角色（角色不同津贴标准不同）
    public static final String ROLE_CHIEF = "CHIEF";
    public static final String ROLE_INVIGILATOR = "INVIGILATOR";
    public static final String ROLE_PATROL = "PATROL";

    // 人员类别
    public static final String STAFF_PART_TIME = "PART_TIME";
    public static final String STAFF_FULL_TIME = "FULL_TIME";

    // 通用启用/停用
    public static final String ENABLED = "ENABLED";
    public static final String DISABLED = "DISABLED";

    // 排班状态
    public static final String SHIFT_SCHEDULED = "SCHEDULED";
    public static final String SHIFT_CANCELLED = "CANCELLED";

    // 聘任记录（当前版本）状态
    /** 已聘任：随排班生成，尚未签到。 */
    public static final String APPT_APPOINTED = "APPOINTED";
    /** 已换人：本场聘任已由接任者承担（本人不计本场费用）。 */
    public static final String APPT_REPLACED = "REPLACED";
    /** 已签到：场次结束签到，计津贴。 */
    public static final String APPT_CHECKED_IN = "CHECKED_IN";
    /** 缺勤：不计费。 */
    public static final String APPT_ABSENT = "ABSENT";
    /** 已取消：场次取消，不计聘任计费。 */
    public static final String APPT_CANCELLED = "CANCELLED";

    // 聘任版本变更类型
    public static final String CHANGE_CREATE = "CREATE";
    /** 换人：被换下者版本（状态 REPLACED）。 */
    public static final String CHANGE_REPLACE = "REPLACE";
    /** 换人：接任者新版本（状态 APPOINTED）。 */
    public static final String CHANGE_REPLACE_NEW = "REPLACE_NEW";
    public static final String CHANGE_CHECKIN = "CHECKIN";
    public static final String CHANGE_ABSENT = "ABSENT";
    public static final String CHANGE_CANCEL = "CANCEL";

    // 结算单状态
    public static final String SETTLE_DRAFT = "DRAFT";
    public static final String SETTLE_CONFIRMED = "CONFIRMED";

    /** 本人视图请求头：传监考人员编码，仅能看本人聘任与结算；不传为人事全量视图。 */
    public static final String SELF_HEADER = "X-Self-Code";
}
