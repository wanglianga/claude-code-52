package com.nightmarket.power.model;

public enum Role {
    VENDOR("摊主"),
    ELECTRICIAN("电工"),
    ADMIN("市场管理员"),
    SECURITY("安保"),
    CASHIER("收费人员"),
    FIREFIGHTER("消防部门");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public String authority() {
        return "ROLE_" + name();
    }
}
