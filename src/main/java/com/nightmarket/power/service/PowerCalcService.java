package com.nightmarket.power.service;

import com.nightmarket.power.model.*;
import com.nightmarket.power.store.RedisStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用电负载、配电箱占用、风险分计算引擎。
 * 同箱负载 = Σ（在营摊位核定负载 + 已批准临时加电负载），考虑：
 *  - 设备性质负载系数（烤炉 1.0 / 灯牌 0.8 / 音响 0.5 …）
 *  - 摊位类型同时系数（餐饮 1.0 / 零售 0.6 …）
 *  - 同区域高峰系数
 *  - 高风险摊位被限制开启的设备剔除
 */
@Service
public class PowerCalcService {

    /** 同区域高峰时段（19:00-22:00）负载上浮系数 */
    public static final double PEAK_FACTOR = 1.10;
    /** 照明需求若无独立设备，按 300W 基线计入 */
    public static final int LIGHTING_BASELINE_W = 300;
    /** 高峰触发：同区域在营摊位数 */
    public static final int PEAK_ZONE_STALLS = 3;

    private final RedisStore store;

    public PowerCalcService(RedisStore store) {
        this.store = store;
    }

    /** 摊位申报设备的原始总额定功率 */
    public int declaredTotal(PowerApplication app) {
        return app.getDevices().stream().mapToInt(Device::totalPowerW).sum();
    }

    /**
     * 核定负载（W）：Σ 设备功率×数量×设备系数，再乘摊位同时系数；
     * 有照明需求但未申报照明设备的，补照明基线。
     */
    public int effectiveLoad(PowerApplication app) {
        double sum = app.getDevices().stream()
                .mapToDouble(d -> d.totalPowerW() * d.getKind().getLoadFactor())
                .sum();
        boolean hasLighting = app.getDevices().stream().anyMatch(d -> d.getKind() == DeviceKind.LIGHTING);
        if (app.isLightingNeeded() && !hasLighting) {
            sum += LIGHTING_BASELINE_W * DeviceKind.LIGHTING.getLoadFactor();
        }
        return (int) Math.round(sum * app.getStallType().getDiversityFactor());
    }

    /** 单个摊位当前实际计入配电箱的负载（含已批准的临时加电，剔除被风控限制的设备） */
    public int currentStallLoad(PowerApplication app) {
        RiskControl risk = store.activeRiskOfStall(app.getStallNo()).orElse(null);
        double base = app.getDevices().stream()
                .filter(d -> risk == null || !risk.getRestrictedDevices().contains(d.getName()))
                .mapToDouble(d -> d.totalPowerW() * d.getKind().getLoadFactor())
                .sum();
        boolean hasLighting = app.getDevices().stream().anyMatch(d -> d.getKind() == DeviceKind.LIGHTING);
        if (app.isLightingNeeded() && !hasLighting
                && (risk == null || !risk.getRestrictedDevices().contains("基础照明"))) {
            base += LIGHTING_BASELINE_W * DeviceKind.LIGHTING.getLoadFactor();
        }
        int load = (int) Math.round(base * app.getStallType().getDiversityFactor());
        for (TempPowerRequest t : store.tempsByApp(app.getId())) {
            if (Statuses.TEMP_ALLOWED.equals(t.getDecision())
                    && (risk == null || !risk.getRestrictedDevices().contains(t.getDeviceName()))) {
                load += t.getAddedLoadW();
            }
        }
        return load;
    }

    /** 配电箱下在营摊位实时负载 */
    public int boxOperatingLoad(String boxId) {
        return store.findByBox(boxId).stream()
                .filter(a -> Statuses.OPERATING.equals(a.getStatus()))
                .mapToInt(this::currentStallLoad)
                .sum();
    }

    /**
     * 审批预测：该箱已批准/在营摊位负载 + 某新申请负载（按高峰系数上浮）。
     */
    public int boxProjectedLoad(String boxId, PowerApplication applicant) {
        int existing = store.findByBox(boxId).stream()
                .filter(a -> !a.getId().equals(applicant.getId()))
                .filter(a -> Statuses.APPROVED.equals(a.getStatus()) || Statuses.OPERATING.equals(a.getStatus()))
                .mapToInt(this::effectiveLoad)
                .sum();
        int projected = existing + effectiveLoad(applicant);
        if (zoneInPeak(applicant.getZone())) {
            projected = (int) Math.round(projected * PEAK_FACTOR);
        }
        return projected;
    }

    /** 临时加电预测：在营负载 + 新增设备负载 */
    public int boxProjectedForTemp(String boxId, int addedLoadW) {
        return boxOperatingLoad(boxId) + addedLoadW;
    }

    /** 同区域是否处于高峰：区域内在营摊位数达到阈值即视为晚高峰 */
    public boolean zoneInPeak(String zone) {
        long operating = store.allApplications().stream()
                .filter(a -> zone.equals(a.getZone()))
                .filter(a -> Statuses.OPERATING.equals(a.getStatus())
                        || Statuses.APPROVED.equals(a.getStatus()))
                .count();
        return operating >= PEAK_ZONE_STALLS;
    }

    /**
     * 风险评分与风险因子。依据：摊位类型、明火、冷柜、申报功率、
     * 历史违规扣分、同区域高峰、配电箱状态。
     */
    public RiskAssessment assess(PowerApplication app, ElectricBox box) {
        RiskAssessment r = new RiskAssessment();
        r.score += app.getStallType().getRiskBase();
        r.factors.add("摊位类型[" + app.getStallType().getLabel() + "]基础分 " + app.getStallType().getRiskBase());

        if (app.isOpenFlame()) {
            r.score += 20;
            r.factors.add("使用明火 +20");
        }
        if (app.isFreezerNeeded()) {
            r.score += 6;
            r.factors.add("冷柜持续用电 +6");
        }
        int declared = declaredTotal(app);
        if (declared > 3000) {
            r.score += 10;
            r.factors.add("申报总额定功率 " + declared + "W（>3kW）+10");
        } else if (declared > 1500) {
            r.score += 5;
            r.factors.add("申报总额定功率 " + declared + "W（>1.5kW）+5");
        }

        int history = store.allViolations().stream()
                .filter(v -> v.getVendorUsername() != null
                        && v.getVendorUsername().equalsIgnoreCase(app.getVendorUsername()))
                .mapToInt(ViolationRecord::getPoints).sum();
        if (history > 0) {
            int add = Math.min(30, history / 2);
            r.score += add;
            r.factors.add("历史违规累计 " + history + " 分 +" + add);
        }
        if (zoneInPeak(app.getZone())) {
            r.score += 5;
            r.factors.add("同区域晚高峰用电叠加 +5");
        }
        if (box != null && "RESTRICTED".equals(box.getStatus())) {
            r.score += 10;
            r.factors.add("拟接配电箱[" + box.getCode() + "]处于限制状态 +10");
        }
        if (box != null) {
            int projected = boxProjectedLoad(box.getId(), app);
            double usage = box.safeCapacityW() == 0 ? 0 : (double) projected / box.safeCapacityW();
            if (usage >= 0.9) {
                r.score += 8;
                r.factors.add("审批后同箱负载率 " + pct(usage) + "（≥90%）+8");
            }
        }
        r.highRisk = r.score >= 40;
        return r;
    }

    /** 选择区域内预测负载率最低且可容纳的非备用配电箱；无可用返回 null */
    public ElectricBox selectBox(PowerApplication app) {
        ElectricBox best = null;
        double bestRatio = Double.MAX_VALUE;
        long bestAssigned = Long.MAX_VALUE;
        for (ElectricBox b : store.allBoxes()) {
            if (b.isBackupLine() || !b.getZone().equals(app.getZone()) || "FAULT".equals(b.getStatus())) {
                continue;
            }
            int projected = boxProjectedLoad(b.getId(), app);
            if (projected > b.safeCapacityW()) {
                continue;
            }
            double ratio = b.safeCapacityW() == 0 ? 0 : (double) projected / b.safeCapacityW();
            long assigned = store.findByBox(b.getId()).stream()
                    .filter(a -> !Statuses.REJECTED.equals(a.getStatus())
                            && !Statuses.CLOSED.equals(a.getStatus()))
                    .count();
            if (ratio < bestRatio - 1e-9 || (Math.abs(ratio - bestRatio) <= 1e-9 && assigned < bestAssigned)) {
                bestRatio = ratio;
                bestAssigned = assigned;
                best = b;
            }
        }
        return best;
    }

    /** 列出各箱占用（页面/接口用） */
    public List<BoxUsage> boxUsages() {
        return store.allBoxes().stream().map(b -> {
            BoxUsage u = new BoxUsage();
            u.box = b;
            u.operatingLoadW = boxOperatingLoad(b.getId());
            u.safeCapacityW = b.safeCapacityW();
            u.usageRate = b.safeCapacityW() == 0 ? 0 : (double) u.operatingLoadW / b.safeCapacityW();
            u.stallCount = (int) store.findByBox(b.getId()).stream()
                    .filter(a -> Statuses.OPERATING.equals(a.getStatus())).count();
            return u;
        }).toList();
    }

    private String pct(double d) {
        return (int) Math.round(d * 100) + "%";
    }

    public static class RiskAssessment {
        public int score;
        public boolean highRisk;
        public java.util.List<String> factors = new java.util.ArrayList<>();
    }

    public static class BoxUsage {
        public ElectricBox box;
        public int operatingLoadW;
        public int safeCapacityW;
        public double usageRate;
        public long stallCount;
    }
}
