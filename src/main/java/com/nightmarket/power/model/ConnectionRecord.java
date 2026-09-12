package com.nightmarket.power.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 电工现场接线记录 */
@Data
@NoArgsConstructor
public class ConnectionRecord {
    private String id;
    private String applicationId;
    private String stallNo;
    private String electricianUsername;
    private String socketNo;            // 插座编号
    private String rcdStatus;           // 漏保（漏电保护器）状态
    private int meterInitialWh;         // 电表初值
    private String cablePhotoUrl;       // 线缆照片
    private String signatureUrl;        // 摊主签字
    private boolean vendorSigned;
    private String remark;
    private LocalDateTime connectedAt;

    public ConnectionRecord(String id, String applicationId, String stallNo) {
        this.id = id;
        this.applicationId = applicationId;
        this.stallNo = stallNo;
        this.connectedAt = LocalDateTime.now();
    }
}
