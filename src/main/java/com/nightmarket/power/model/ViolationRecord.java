package com.nightmarket.power.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 违规扣分记录（事件认定或巡查发现后写入摊位档案） */
@Data
@NoArgsConstructor
public class ViolationRecord {
    private String id;
    private String stallNo;
    private String vendorUsername;
    private String incidentId;          // 可空（巡查直接开具）
    private IncidentType type;
    private int points;
    private Responsibility responsibility;
    private String detail;
    private String recorder;
    private Role recorderRole;
    private String rectifyPhotoUrl;
    private LocalDateTime createdAt;

    public ViolationRecord(String id) {
        this.id = id;
        this.createdAt = LocalDateTime.now();
    }
}
