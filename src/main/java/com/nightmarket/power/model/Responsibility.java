package com.nightmarket.power.model;

/** 跳闸/险情责任方 */
public enum Responsibility {
    VENDOR("摊主"),
    ELECTRICIAN("电工"),
    MARKET("市场方"),
    FORCE_MAJURE("不可抗力");

    private final String label;

    Responsibility(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static Responsibility fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return Responsibility.valueOf(code.trim().toUpperCase());
    }
}
