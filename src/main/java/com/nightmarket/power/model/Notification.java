package com.nightmarket.power.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 站内通知：临时加电审批结果推送给同配电箱其他摊主、电工、收费人员。
 * 内容必须说明加电摊位与风险时段，便于邻近摊主配合避峰。
 */
@Data
@NoArgsConstructor
public class Notification {
    private String id;
    private String targetVendor;        // 接收人账号
    private Role targetRole;            // 接收人角色（展示用）
    private String category;            // TEMP_APPROVED / TEMP_PENDING / TEMP_DENIED / TEMP_EFFECTIVE / HOOKUP_TASK / FEE
    private String boxId;
    private String sourceStall;         // 加电摊位
    private String title;
    private String content;
    private LocalDateTime riskFrom;     // 风险起始（通常为接线生效时刻）
    private String riskUntil;           // 风险截止文案（如“今晚24:00”）
    private boolean read;
    private LocalDateTime createdAt;

    public Notification(String id) {
        this.id = id;
        this.createdAt = LocalDateTime.now();
    }
}
