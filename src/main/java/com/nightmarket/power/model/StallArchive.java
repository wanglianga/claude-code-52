package com.nightmarket.power.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 闭市后摊位档案：用电量 / 扣分 / 加电费 / 跳闸责任 / 整改照片，用于下期分配与评级 */
@Data
@NoArgsConstructor
public class StallArchive {
    private String id;
    private String stallNo;
    private String stallName;
    private String vendorUsername;
    private String zone;
    private StallType stallType;
    private int meterStartWh;
    private int meterEndWh;
    private int consumedWh;
    private double electricityFeeWan;
    private int violationPoints;
    private List<String> violations = new ArrayList<>();
    private int tempAddCount;
    private double tempAddFeeWan;
    private int incidentCount;
    private List<String> incidentResponsibilities = new ArrayList<>();
    private List<String> rectifyPhotos = new ArrayList<>();
    private String grade;                 // A/B/C/D
    private int allocationScore;          // 下期摊位分配积分
    private String advice;                // 下期分配建议
    private boolean withdrawnEarly;       // 是否夜间提前撤摊
    private LocalDateTime withdrawnAt;    // 撤摊时间
    private LocalDateTime archivedAt;

    public StallArchive(String id) {
        this.id = id;
        this.archivedAt = LocalDateTime.now();
    }
}
