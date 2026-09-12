package com.nightmarket.power.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 营业中临时加电申请（烤炉/冰柜/灯牌/音响…），自动重算同箱负载 */
@Data
@NoArgsConstructor
public class TempPowerRequest {
    private String id;
    private String applicationId;
    private String stallNo;
    private String vendorUsername;
    private DeviceKind kind;
    private String deviceName;
    private int ratedPowerW;
    private int quantity = 1;
    private double diversityFactor;
    private int addedLoadW;                 // 本次计入负载
    private int boxLoadBeforeW;
    private int boxLoadAfterW;
    private int boxSafeCapacityW;
    private String decision = Statuses.DECISION_PENDING; // ALLOWED / DENIED
    private String reason;                  // 允许/驳回原因
    private double fee;                     // 临时加电费
    private String decidedBy;               // 系统自动 / 管理员
    private LocalDateTime createdAt;

    public TempPowerRequest(String id) {
        this.id = id;
        this.createdAt = LocalDateTime.now();
    }
}
