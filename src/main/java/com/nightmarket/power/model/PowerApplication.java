package com.nightmarket.power.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 摊主报名时提交的临时用电申请。
 */
@Data
@NoArgsConstructor
public class PowerApplication {
    private String id;
    private String stallNo;               // 摊位号
    private String stallName;             // 摊位名称
    private String vendorUsername;        // 摊主
    private String zone;                  // 所在区域
    private StallType stallType;
    private List<Device> devices = new ArrayList<>();
    private boolean openFlame;            // 是否使用明火
    private boolean freezerNeeded;        // 冷柜需求
    private boolean lightingNeeded;       // 照明需求
    private String businessHours;         // 营业时段，如 18:00-23:00
    private int declaredTotalW;           // 申报设备总功率（未计系数）

    private String status = Statuses.PENDING;
    private String boxId;                 // 分配的配电箱
    private String decisionBy;
    private LocalDateTime decisionAt;
    private String decisionRemark;
    private int riskScore;                // 风险分
    private List<String> riskFactors = new ArrayList<>(); // 风险因子说明
    private int approvedPowerW;           // 核定可接功率
    private int estimatedBoxLoadW;        // 审批时同箱预估负载
    private boolean highRisk;             // 高风险标记
    private LocalDateTime createdAt;

    /** 夜间闭市结算字段 */
    private int meterStartWh;
    private int meterEndWh;
    private int consumedWh;
    private int violationPoints;
    private int tempAddCount;
    private int tempAddPowerW;
    private double tempAddFee;
    private double electricityFee;
    private String grade;                 // 安全评级 A/B/C/D
    private boolean archived;

    public PowerApplication(String id) {
        this.id = id;
        this.createdAt = LocalDateTime.now();
    }
}
