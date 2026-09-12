package com.nightmarket.power.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 高风险摊位风控措施：限制设备 / 电工复检 / 迁移备用线路，
 * 市场广播与收费调整同步记录。
 */
@Data
@NoArgsConstructor
public class RiskControl {
    private String id;
    private String stallNo;
    private String adminUsername;
    private boolean restrictDevices;        // 临时限制设备开启
    private List<String> restrictedDevices = new ArrayList<>(); // 被限制的设备名
    private boolean requireRecheck;         // 要求电工复检
    private String recheckElectrician;
    private boolean recheckPassed;
    private boolean migrateLine;            // 迁移到备用线路
    private String targetBoxId;
    private String broadcast;               // 市场广播内容
    private double feeAdjustmentWan;        // 收费调整（+加收/-减免）
    private String reason;
    private String status = "ACTIVE";       // ACTIVE / LIFTED
    private LocalDateTime createdAt;
    private LocalDateTime liftedAt;

    public RiskControl(String id) {
        this.id = id;
        this.createdAt = LocalDateTime.now();
    }
}
