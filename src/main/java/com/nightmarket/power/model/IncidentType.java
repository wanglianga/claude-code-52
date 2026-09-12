package com.nightmarket.power.model;

/** 用电事件（故障/险情）类型，带默认扣分与责任建议 */
public enum IncidentType {
    TRIP("跳闸", 6, Responsibility.MARKET),
    OVERHEAT("线路发热", 8, Responsibility.ELECTRICIAN),
    PRIVATE_WIRING("私拉电线", 12, Responsibility.VENDOR),
    WATERLOG("雨水浸泡", 2, Responsibility.FORCE_MAJURE),
    ODOR_COMPLAINT("顾客投诉异味", 3, Responsibility.VENDOR),
    FIRE_HAZARD("消防抽检隐患", 6, Responsibility.VENDOR);

    private final String label;
    private final int defaultPoints;
    private final Responsibility suggested;

    IncidentType(String label, int defaultPoints, Responsibility suggested) {
        this.label = label;
        this.defaultPoints = defaultPoints;
        this.suggested = suggested;
    }

    public String getLabel() {
        return label;
    }

    public int getDefaultPoints() {
        return defaultPoints;
    }

    public Responsibility getSuggested() {
        return suggested;
    }

    public static IncidentType fromCode(String code) {
        return IncidentType.valueOf(code.trim().toUpperCase());
    }
}
