package com.nightmarket.power.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 申报/加装的用电设备 */
@Data
@NoArgsConstructor
public class Device {
    private DeviceKind kind;
    private String name;
    private int ratedPowerW;        // 额定功率（瓦）
    private int quantity = 1;

    public Device(DeviceKind kind, String name, int ratedPowerW, int quantity) {
        this.kind = kind;
        this.name = name;
        this.ratedPowerW = ratedPowerW;
        this.quantity = quantity;
    }

    public int totalPowerW() {
        return ratedPowerW * quantity;
    }
}
