package com.nightmarket.power.model;

/** 用电设备种类：临时加电时按性质取负载系数 */
public enum DeviceKind {
    STOVE("烤炉/电热", 1.00),
    FREEZER("冰柜/冷柜", 1.00),
    LIGHTBOX("灯牌/广告灯", 0.80),
    SPEAKER("音响设备", 0.50),
    LIGHTING("照明", 0.90),
    EQUIP("其他设备", 0.90);

    private final String label;
    private final double loadFactor;

    DeviceKind(String label, double loadFactor) {
        this.label = label;
        this.loadFactor = loadFactor;
    }

    public String getLabel() {
        return label;
    }

    public double getLoadFactor() {
        return loadFactor;
    }

    public static DeviceKind fromCode(String code) {
        if (code == null) {
            return EQUIP;
        }
        try {
            return DeviceKind.valueOf(code.trim().toUpperCase());
        } catch (Exception e) {
            return EQUIP;
        }
    }
}
