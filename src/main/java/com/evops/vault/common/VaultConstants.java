package com.evops.vault.common;

/**
 * 保密室双人复核登记链业务常量：进出方向、登记状态、版本变更类型、来源、告警类型、配置键。
 */
public final class VaultConstants {

    private VaultConstants() {
    }

    // 通用启用/停用
    public static final String ENABLED = "ENABLED";
    public static final String DISABLED = "DISABLED";

    // 进出方向
    public static final String DIRECTION_IN = "IN";
    public static final String DIRECTION_OUT = "OUT";

    // 出入登记状态（当前版本状态）
    /** 待复核：刷证登记后未生效，不计入在室清单。 */
    public static final String ACCESS_PENDING = "PENDING";
    /** 已生效：第二人复核确认，计入在室清单与卷箱在室口径。 */
    public static final String ACCESS_CONFIRMED = "CONFIRMED";
    /** 已驳回：复核不通过，永不生效。 */
    public static final String ACCESS_REJECTED = "REJECTED";

    // 出入登记版本变更类型
    public static final String CHANGE_CREATE = "CREATE";
    public static final String CHANGE_CONFIRM = "CONFIRM";
    public static final String CHANGE_REJECT = "REJECT";
    /** 纸面存量导入：历史既成事实，直接落生效版。 */
    public static final String CHANGE_IMPORT = "IMPORT";

    // 登记来源
    public static final String SOURCE_LIVE = "LIVE";
    public static final String SOURCE_PAPER = "PAPER";

    // 补充说明类型
    public static final String NOTE_SUPPLEMENT = "SUPPLEMENT";
    public static final String NOTE_CORRECTION = "CORRECTION";

    // 保卫告警类型
    /** 异常时段进出：发生时间落在允许窗口外。 */
    public static final String ALERT_OFF_HOURS = "OFF_HOURS";
    public static final String ALERT_NEW = "NEW";
    public static final String ALERT_ACKED = "ACKED";

    // 配置键：允许进出时段（HH:mm，起点含、终点不含）
    public static final String CFG_WINDOW_START = "ACCESS_WINDOW_START";
    public static final String CFG_WINDOW_END = "ACCESS_WINDOW_END";
    public static final String DEFAULT_WINDOW_START = "07:00";
    public static final String DEFAULT_WINDOW_END = "19:00";
}
