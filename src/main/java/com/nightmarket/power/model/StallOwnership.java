package com.nightmarket.power.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 摊位归属与现场变动：夜间撤摊 / 临时转让 / 多摊共用设备，
 * 确保用电责任链不断开。
 */
@Data
@NoArgsConstructor
public class StallOwnership {
    private String stallNo;
    private String originalVendor;        // 原摊主
    private String currentVendor;         // 当前责任人（转让后为受让方）
    private boolean withdrawn;            // 是否已夜间撤摊
    private LocalDateTime withdrawnAt;
    private String transferTo;            // 受让摊主
    private LocalDateTime transferredAt;
    private String transferRemark;
    private String sharedWithStall;       // 共用设备的另一摊位
    private String sharedDeviceDesc;      // 共用设备描述
    private LocalDateTime createdAt;

    public StallOwnership(String stallNo, String vendor) {
        this.stallNo = stallNo;
        this.originalVendor = vendor;
        this.currentVendor = vendor;
        this.createdAt = LocalDateTime.now();
    }
}
