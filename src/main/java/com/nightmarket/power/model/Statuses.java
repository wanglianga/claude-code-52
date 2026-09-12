package com.nightmarket.power.model;

/** 摊位状态常量 */
public final class Statuses {
    private Statuses() {
    }

    public static final String PENDING = "PENDING";            // 待审批
    public static final String APPROVED = "APPROVED";          // 已批准待接线
    public static final String REJECTED = "REJECTED";          // 审批驳回
    public static final String OPERATING = "OPERATING";        // 已送电营业
    public static final String POWER_CUT = "POWER_CUT";        // 故障断电中
    public static final String SUSPENDED = "SUSPENDED";        // 被责令停业
    public static final String MIGRATING = "MIGRATING";        // 迁移备用线路中
    public static final String CLOSED = "CLOSED";              // 已撤摊/闭市

    public static final String DECISION_PENDING = "PENDING";
    public static final String DECISION_APPROVED = "APPROVED";
    public static final String DECISION_REJECTED = "REJECTED";

    public static final String TEMP_ALLOWED = "ALLOWED";
    public static final String TEMP_DENIED = "DENIED";

    public static final String EVENT_OPEN = "OPEN";
    public static final String EVENT_RECOVERED = "RECOVERED";
    public static final String EVENT_CLOSED = "CLOSED";

    public static String label(String s) {
        return switch (s) {
            case PENDING -> "待审批";
            case APPROVED -> "已批准·待接线";
            case REJECTED -> "已驳回";
            case OPERATING -> "营业中";
            case POWER_CUT -> "故障断电";
            case SUSPENDED -> "责令停业";
            case MIGRATING -> "迁线中";
            case CLOSED -> "已撤摊";
            default -> s;
        };
    }
}
