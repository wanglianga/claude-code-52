package com.nightmarket.power.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 营业中临时加电申请（烤炉/冰柜/灯牌/音响…）。
 * 两阶段：系统/管理员审批 → 电工到场接线生效。
 * 审批检查：同配电箱剩余容量、邻近摊位负载（冷柜保护）、电工到场时间。
 */
@Data
@NoArgsConstructor
public class TempPowerRequest {
    private String id;
    private String applicationId;
    private String stallNo;
    private String stallName;
    private String zone;
    private String vendorUsername;
    private DeviceKind kind;
    private String deviceName;
    private int ratedPowerW;
    private int quantity = 1;
    private double diversityFactor;
    private int addedLoadW;                 // 本次计入负载

    // ---- 同箱负载重算 ----
    private String boxId;
    private int boxLoadBeforeW;
    private int boxLoadAfterW;
    private int boxSafeCapacityW;
    private int boxRemainingW;             // 剩余安全容量
    private double boxUsageAfter;          // 加电后负载率
    private List<NeighborLoad> neighbors = new ArrayList<>(); // 同箱邻近摊位负载
    private boolean hasNeighborFreezer;
    private int neighborFreezerLoadW;

    // ---- 电工到场 ----
    private String electricianUsername;
    private int etaMinutes;                 // 预计到场分钟
    private LocalDateTime requestedArrivalBefore;
    private String arrivalEligible;         // OK / BUSY（现场判定前的预估）

    /**
     * 审批结论：
     * PENDING_APPROVAL 已受理待电工接线（审批通过）
     * ALLOWED          已接线生效（保留旧语义：最终成功）
     * DENIED           审批拒绝（含替代建议）
     */
    private String decision = "PENDING";
    private String reason;                  // 允许/驳回原因
    private List<String> suggestions = new ArrayList<>(); // 拒绝时替代位置/设备建议
    private String safetyTip;               // 批准时安全提示
    private double fee;                     // 临时加电费（仅审批通过计收，拒绝一律为 0）
    private boolean charged;                // 是否实际收费（拒绝/未接线均为 false）
    private String decidedBy;
    private LocalDateTime decidedAt;

    // ---- 电工到场接线（生效） ----
    private boolean effective;              // 是否真正接线送电
    private String hookupSocketNo;
    private String hookupPhotoUrl;
    private LocalDateTime effectiveAt;
    private LocalDateTime riskUntil;        // 风险时段截止（今晚营业结束）

    private LocalDateTime createdAt;

    public TempPowerRequest(String id) {
        this.id = id;
        this.createdAt = LocalDateTime.now();
    }

    @Data
    @NoArgsConstructor
    public static class NeighborLoad {
        private String stallNo;
        private String stallName;
        private int loadW;
        private boolean freezer;
        private boolean highLoad;

        public NeighborLoad(String stallNo, String stallName, int loadW, boolean freezer, boolean highLoad) {
            this.stallNo = stallNo;
            this.stallName = stallName;
            this.loadW = loadW;
            this.freezer = freezer;
            this.highLoad = highLoad;
        }
    }
}
