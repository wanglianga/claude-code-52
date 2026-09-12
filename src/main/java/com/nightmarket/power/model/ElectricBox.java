package com.nightmarket.power.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 配电箱。safetyMargin=安全余量系数（默认 0.8，即持续负载不超过额定 80%）。
 */
@Data
@NoArgsConstructor
public class ElectricBox {
    private String id;
    private String zone;                  // 区域
    private String code;                  // 箱号
    private int capacityW;                // 额定容量 W
    private double safetyMargin = 0.8;    // 允许负载比例
    private boolean backupLine;           // 是否备用线路箱
    private String status = "NORMAL";     // NORMAL / RESTRICTED / FAULT
    private LocalDateTime createdAt;

    public ElectricBox(String id, String zone, String code, int capacityW, boolean backupLine) {
        this.id = id;
        this.zone = zone;
        this.code = code;
        this.capacityW = capacityW;
        this.backupLine = backupLine;
        this.createdAt = LocalDateTime.now();
    }

    public int safeCapacityW() {
        return (int) Math.round(capacityW * safetyMargin);
    }
}
