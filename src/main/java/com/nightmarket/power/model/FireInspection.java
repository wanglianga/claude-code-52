package com.nightmarket.power.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 消防部门抽检记录 */
@Data
@NoArgsConstructor
public class FireInspection {
    private String id;
    private String stallNo;
    private String firefighterUsername;
    private InspectionResult result;
    private String findings;        // 检查发现
    private int deductPoints;
    private String rectifyPhotoUrl;
    private LocalDateTime createdAt;

    public FireInspection(String id) {
        this.id = id;
        this.createdAt = LocalDateTime.now();
    }
}
