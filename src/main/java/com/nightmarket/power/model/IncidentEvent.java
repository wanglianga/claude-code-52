package com.nightmarket.power.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用电事件：跳闸 / 线路发热 / 私拉电线 / 雨水浸泡 / 顾客投诉异味。
 * 把摊主、电工、管理员、安保、收费人员串到同一事件中。
 */
@Data
@NoArgsConstructor
public class IncidentEvent {
    private String id;
    private IncidentType type;
    private String boxId;
    private String primaryStallNo;
    private String reporter;                       // 报告人
    private Role reporterRole;
    private String description;
    private String status = Statuses.EVENT_OPEN;   // OPEN / RECOVERED / CLOSED
    private int severity = 3;                       // 1-5

    // ---- 同一事件串联的人员 ----
    private String vendorUsername;
    private String electricianUsername;
    private String adminUsername;
    private String securityUsername;
    private String cashierUsername;

    // ---- 受影响摊位（同箱） ----
    private List<String> affectedStalls = new ArrayList<>();

    // ---- 恢复供电前核查清单 ----
    private String faultPoint;                     // 故障点确认
    private String faultConfirmedBy;               // 电工
    private Integer foodLossWan;                   // 食品损耗（元）
    private Integer customerCompensationWan;       // 顾客赔付（元）
    private String stallSuspended;                 // 被暂停营业的摊位号
    private boolean rectified;                     // 整改完成
    private String rectifyPhotoUrl;                // 整改照片
    private String recoveredBy;
    private LocalDateTime recoveredAt;

    // ---- 责任认定 ----
    private Responsibility responsibility;
    private Integer deductPoints;                  // 扣分
    private String liabilityRemark;

    // ---- 时间线（各方处理记录） ----
    private List<TimelineEntry> timeline = new ArrayList<>();

    private LocalDateTime createdAt;

    public IncidentEvent(String id) {
        this.id = id;
        this.createdAt = LocalDateTime.now();
    }

    public void addTimeline(String actor, Role role, String action) {
        timeline.add(new TimelineEntry(actor, role, action, LocalDateTime.now()));
    }

    @Data
    @NoArgsConstructor
    public static class TimelineEntry {
        private String actor;
        private Role actorRole;
        private String action;
        private LocalDateTime at;

        public TimelineEntry(String actor, Role actorRole, String action, LocalDateTime at) {
            this.actor = actor;
            this.actorRole = actorRole;
            this.action = action;
            this.at = at;
        }
    }
}
