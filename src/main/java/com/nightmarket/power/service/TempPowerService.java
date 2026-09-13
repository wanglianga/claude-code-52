package com.nightmarket.power.service;

import com.nightmarket.power.model.*;
import com.nightmarket.power.store.RedisStore;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 营业中临时加电审批（两阶段）：
 *  1) 摊主申请：检查同配电箱剩余容量、邻近摊位负载（冷柜保护）、电工到场时间；
 *     通过 → 生成待接线任务并通知电工与同箱其他摊主（说明加电摊位与风险时段）；
 *     拒绝 → 给出替代位置（备用线路箱）/设备（降功率/错峰）建议。
 *  2) 电工到场接线：照片+收费+安全提示写入当晚摊位记录，并再次广播生效通知。
 */
@Service
public class TempPowerService {

    /** 临时加电费：每 100W 计收 2 元（演示费率） */
    public static final double FEE_PER_100W = 2.0;
    /** 电工基础到场时间（分钟） */
    public static final int BASE_ETA_MINUTES = 10;
    /** 每个待接线任务叠加的到场时间 */
    public static final int ETA_PER_PENDING = 8;
    /** 默认可接受最长等待（分钟），摊主可在申请中指定 */
    public static final int DEFAULT_MAX_WAIT = 15;
    /** 同箱存在冷柜且加电后负载率达到该值，为保护邻近冷柜拒绝 */
    public static final double FREEZER_PROTECT_RATE = 0.90;

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final RedisStore store;
    private final PowerCalcService calc;
    private final BillingService billing;
    private final DirectoryService directory;
    private final NotificationService notifications;

    public TempPowerService(RedisStore store, PowerCalcService calc, BillingService billing,
                            DirectoryService directory, NotificationService notifications) {
        this.store = store;
        this.calc = calc;
        this.billing = billing;
        this.directory = directory;
        this.notifications = notifications;
    }

    /** 审批通过待接线 */
    public static final String PENDING_APPROVAL = "PENDING_APPROVAL";

    public TempPowerRequest request(String appId, String vendor, DeviceKind kind, String deviceName,
                                    int ratedPowerW, int quantity, Integer maxWaitMinutes) {
        PowerApplication app = mustOperating(appId);
        if (vendor != null && !app.getVendorUsername().equalsIgnoreCase(vendor)
                && !"ADMIN".equals(directoryRole(vendor))) {
            throw new BizException("只能为自己名下的摊位申请临时加电");
        }
        RiskControl risk = store.activeRiskOfStall(app.getStallNo()).orElse(null);
        if (risk != null && risk.isRestrictDevices()) {
            TempPowerRequest denied = baseRequest(app, kind, deviceName, ratedPowerW, quantity);
            denied.setDecision(Statuses.TEMP_DENIED);
            denied.setReason("管理员已对该摊位临时限制设备开启，暂不允许新增设备");
            denied.getSuggestions().add("联系市场管理员解除设备限制后再申请");
            store.saveTemp(denied);
            return denied;
        }
        ElectricBox box = store.getBox(app.getBoxId());

        TempPowerRequest t = baseRequest(app, kind, deviceName, ratedPowerW, quantity);
        t.setBoxId(box.getId());

        // ---- 检查一：同配电箱剩余容量 ----
        int before = calc.boxOperatingLoad(box.getId());
        int after = before + t.getAddedLoadW();
        int remaining = box.safeCapacityW() - before;
        t.setBoxLoadBeforeW(before);
        t.setBoxLoadAfterW(after);
        t.setBoxSafeCapacityW(box.safeCapacityW());
        t.setBoxRemainingW(Math.max(0, remaining));
        t.setBoxUsageAfter(box.safeCapacityW() == 0 ? 0 : (double) after / box.safeCapacityW());

        // 邻近摊位负载
        List<TempPowerRequest.NeighborLoad> neighbors = new ArrayList<>();
        boolean neighborFreezer = false;
        int freezerLoad = 0;
        for (PowerApplication other : store.findByBox(box.getId())) {
            if (other.getId().equals(app.getId()) || !Statuses.OPERATING.equals(other.getStatus())) {
                continue;
            }
            int load = calc.currentStallLoad(other);
            boolean freezer = other.isFreezerNeeded()
                    || other.getDevices().stream().anyMatch(d -> d.getKind() == DeviceKind.FREEZER);
            boolean high = load >= 1500;
            neighbors.add(new TempPowerRequest.NeighborLoad(other.getStallNo(), other.getStallName(),
                    load, freezer, high));
            if (freezer) {
                neighborFreezer = true;
                freezerLoad += load;
            }
        }
        t.setNeighbors(neighbors);
        t.setHasNeighborFreezer(neighborFreezer);
        t.setNeighborFreezerLoadW(freezerLoad);

        // ---- 检查三：电工到场时间 ----
        int pendingTasks = (int) store.allTemps().stream()
                .filter(x -> PENDING_APPROVAL.equals(x.getDecision())).count();
        int eta = BASE_ETA_MINUTES + pendingTasks * ETA_PER_PENDING;
        boolean boxInIncident = store.allEvents().stream()
                .anyMatch(e -> box.getId().equals(e.getBoxId()) && Statuses.EVENT_OPEN.equals(e.getStatus()));
        if (boxInIncident) {
            eta = -1; // 同箱故障处置中，无法安排
        }
        User elec = directory.duty(Role.ELECTRICIAN);
        t.setElectricianUsername(elec == null ? null : elec.getUsername());
        t.setEtaMinutes(Math.max(eta, 0));
        t.setArrivalEligible(boxInIncident ? "BUSY" : "OK");
        int maxWait = maxWaitMinutes == null || maxWaitMinutes <= 0 ? DEFAULT_MAX_WAIT : maxWaitMinutes;
        LocalDateTime riskFrom = LocalDateTime.now().plusMinutes(Math.max(eta, 0));
        LocalDateTime riskUntil = businessClose(app.getBusinessHours());
        t.setRequestedArrivalBefore(LocalDateTime.now().plusMinutes(maxWait));
        t.setRiskUntil(riskUntil);

        double fee = Math.ceil(t.getAddedLoadW() / 100.0) * FEE_PER_100W;
        t.setFee(fee);
        String riskWindow = "电工接线后约 " + riskFrom.toLocalTime().format(HM) + " 起至今晚 "
                + riskUntil.toLocalTime().format(HM);

        // ---- 综合裁决 ----
        List<String> suggestions = new ArrayList<>();
        List<String> rejectReasons = new ArrayList<>();

        if (boxInIncident) {
            rejectReasons.add("同配电箱[" + box.getCode() + "]故障处置中，电工无法到场接线，需待恢复供电后再申请");
            suggestions.add("等待故障恢复、配电箱复位后重新提交");
        }
        if (after > box.safeCapacityW()) {
            rejectReasons.add("同箱重算负载 " + after + "W 超过安全容量 " + box.safeCapacityW()
                    + "W（当前 " + before + "W + 本次 " + t.getAddedLoadW() + "W，剩余仅 "
                    + Math.max(0, remaining) + "W）");
            suggestions.add("降低功率/数量：本次设备计入负载需 ≤ " + Math.max(0, remaining)
                    + "W（申报额定约 ≤ " + maxRatedForRemaining(app, kind, remaining) + "W），可换小功率电扒炉/保温炉");
            suggestions.add("错峰使用：避开 19:00-22:00 晚高峰，22:00 后同箱负载下降再申请");
            String backup = suggestBackupBox(t.getAddedLoadW());
            if (backup != null) {
                suggestions.add("更换位置：申请把摊位临时设备接到备用线路 " + backup);
            }
        } else if (neighborFreezer && t.getBoxUsageAfter() >= FREEZER_PROTECT_RATE) {
            rejectReasons.add("同箱有邻近摊位冷柜持续运行（冷柜负载约 " + freezerLoad
                    + "W），加电后箱负载率将达 " + pct(t.getBoxUsageAfter())
                    + "，晚高峰可能致冷柜跳闸、食材损耗，为保护邻近摊位拒绝");
            suggestions.add("更换设备：改用更低功率设备，使加电后负载率低于 90%（本次需再降约 "
                    + (after - (int) Math.round(box.safeCapacityW() * FREEZER_PROTECT_RATE)) + "W）");
            suggestions.add("错峰：邻近冷柜摊位收摊后（约 22:30）再开启烤炉");
            String backup = suggestBackupBox(t.getAddedLoadW());
            if (backup != null) {
                suggestions.add("更换位置：迁移到备用线路 " + backup + " 独立供电");
            }
        }
        if (!boxInIncident && eta > maxWait) {
            rejectReasons.add("值班电工预计 " + eta + " 分钟才能到场，超过可接受等待 " + maxWait
                    + " 分钟（当前有 " + pendingTasks + " 个待接线任务排队）");
            suggestions.add("错峰：" + (riskFrom.getHour() >= 22 ? "稍后" : "21:30 后")
                    + "电工排队缓解再申请，或联系管理员增派值班电工");
        }
        if (billing.isInsufficient(app.getVendorUsername(), fee)) {
            rejectReasons.add("电费预付余额不足以支付临时加电费 " + fee + " 元，请先充值");
            suggestions.add("到收费台充值后重新申请（账户与名下全部摊位共用）");
        }

        if (!rejectReasons.isEmpty()) {
            t.setDecision(Statuses.TEMP_DENIED);
            t.setReason("临时加电审批未通过：" + String.join("；", rejectReasons));
            t.setSuggestions(suggestions);
            t.setDecidedBy("SYSTEM");
            t.setDecidedAt(LocalDateTime.now());
            store.saveTemp(t);
            return t;
        }

        // ---- 审批通过：待电工到场接线 ----
        t.setDecision(PENDING_APPROVAL);
        t.setDecidedBy("SYSTEM");
        t.setDecidedAt(LocalDateTime.now());
        String tip = "安全提示：新增" + kind.getLabel() + "须由电工现场接线，严禁私拉插线板；"
                + "线缆远离明火与积水，漏保每月试跳；" + riskWindow + " 为高负载风险时段，"
                + "如闻到焦糊味、配电箱跳闸，立即断开本设备并通知市场管理员。";
        t.setSafetyTip(tip);
        t.setReason("审批通过：同箱负载将由 " + before + "W 升至 " + after + "W（负载率 "
                + pct(t.getBoxUsageAfter()) + "），剩余容量 " + (box.safeCapacityW() - after)
                + "W；电工 " + (elec == null ? "值班人员" : elec.getName()) + " 预计 " + eta
                + " 分钟到场；已通知同箱 " + neighbors.size() + " 个邻近摊位避峰配合");
        store.saveTemp(t);

        // 收费在审批通过时即计入当晚摊位记录
        billing.chargeTempAdd(app.getVendorUsername(), fee,
                app.getStallNo() + " 临时加电审批通过：" + kind.getLabel() + "·" + deviceName);
        app.setTempAddCount(app.getTempAddCount() + 1);
        app.setTempAddPowerW(app.getTempAddPowerW() + ratedPowerW * quantity);
        app.setTempAddFee(round2(app.getTempAddFee() + fee));
        store.saveApplication(app);

        notifyApproval(t, app, box, neighbors, eta, riskWindow, tip);
        return t;
    }

    /** 电工到场接线生效：再次校验容量并写入照片/安全提示，广播生效通知 */
    public TempPowerRequest hookup(String tempId, String electrician, String socketNo, String photoUrl) {
        TempPowerRequest t = store.allTemps().stream().filter(x -> x.getId().equals(tempId)).findFirst()
                .orElseThrow(() -> new BizException("临时加电申请不存在: " + tempId));
        if (!PENDING_APPROVAL.equals(t.getDecision())) {
            throw new BizException("该申请状态为 " + t.getDecision() + "，不可接线");
        }
        if (socketNo == null || socketNo.isBlank()) {
            throw new BizException("缺少加电设备插座编号，不能接线");
        }
        if (photoUrl == null || photoUrl.isBlank()) {
            throw new BizException("缺少接线照片，不能送电");
        }
        PowerApplication app = store.getApplication(t.getApplicationId());
        if (!Statuses.OPERATING.equals(app.getStatus())) {
            throw new BizException("摊位 " + app.getStallNo() + " 当前未在营（"
                    + Statuses.label(app.getStatus()) + "），暂缓接线，待恢复营业后处理");
        }
        ElectricBox box = store.getBox(t.getBoxId());
        int nowLoad = calc.boxOperatingLoad(box.getId());
        if (nowLoad + t.getAddedLoadW() > box.safeCapacityW()) {
            t.getSuggestions().clear();
            t.getSuggestions().add("现场情况变化：同箱当前负载已升至 " + nowLoad + "W，接线即超载；请错峰或迁移备用线路后重新申请");
            t.setDecision(Statuses.TEMP_DENIED);
            t.setReason("电工到场复核：当前同箱负载 " + nowLoad + "W + 本次 " + t.getAddedLoadW()
                    + "W 超过安全容量 " + box.safeCapacityW() + "W，拒绝接线");
            t.setDecidedBy(electrician);
            t.setDecidedAt(LocalDateTime.now());
            store.saveTemp(t);
            // 审批时已扣的加电费原路退还
            billing.refundTempAdd(t.getVendorUsername(), t.getFee(),
                    "临时加电 " + t.getId() + " 到场复核未接线，退还加电费");
            throw new BizException(t.getReason());
        }

        t.setDecision(Statuses.TEMP_ALLOWED);
        t.setEffective(true);
        t.setElectricianUsername(electrician);
        t.setHookupSocketNo(socketNo);
        t.setHookupPhotoUrl(photoUrl);
        t.setEffectiveAt(LocalDateTime.now());
        t.setReason(t.getReason() + "；电工已到场接线送电（插座 " + socketNo + "），收费 "
                + t.getFee() + " 元、接线照片与安全提示已写入当晚摊位记录");
        store.saveTemp(t);

        // 通知同箱其他摊主：风险已实际开始
        for (PowerApplication other : store.findByBox(box.getId())) {
            if (other.getId().equals(app.getId()) || !Statuses.OPERATING.equals(other.getStatus())) {
                continue;
            }
            notifications.push(other.getVendorUsername(), Role.VENDOR, "TEMP_EFFECTIVE", box.getId(),
                    app.getStallNo(),
                    "【加电已生效】" + app.getStallNo() + " 加装" + t.getKind().getLabel() + "已送电",
                    "摊位 " + app.getStallNo() + "（" + app.getStallName() + "）加装的 " + t.getDeviceName()
                            + "（" + t.getRatedPowerW() * t.getQuantity() + "W）已由电工 " + electrician
                            + " 现场接线并送电，同箱当前负载 " + (nowLoad + t.getAddedLoadW()) + "W/"
                            + box.safeCapacityW() + "W。风险时段：即刻起至今晚 "
                            + t.getRiskUntil().toLocalTime().format(HM)
                            + "，请错峰使用大功率设备，冷柜负荷异常或跳闸时风险来源为 " + app.getStallNo()
                            + "，请立即联系市场管理员。",
                    t.getEffectiveAt(), "今晚 " + t.getRiskUntil().toLocalTime().format(HM));
        }
        return t;
    }

    // ------------------------------------------------------------------

    private void notifyApproval(TempPowerRequest t, PowerApplication app, ElectricBox box,
                                List<TempPowerRequest.NeighborLoad> neighbors, int eta,
                                String riskWindow, String tip) {
        // 电工：接线任务
        if (t.getElectricianUsername() != null) {
            notifications.push(t.getElectricianUsername(), Role.ELECTRICIAN, "HOOKUP_TASK",
                    box.getId(), app.getStallNo(),
                    "【加电接线任务】" + app.getStallNo() + " 申请加装" + t.getKind().getLabel(),
                    "摊位 " + app.getStallNo() + "（" + app.getStallName() + "，" + box.getCode()
                            + "）临时加电已审批通过：" + t.getDeviceName() + " "
                            + t.getRatedPowerW() * t.getQuantity() + "W，请于约 " + eta
                            + " 分钟内到场完成独立插座接线、漏保测试并拍照上传。",
                    LocalDateTime.now(), riskWindow);
        }
        // 同箱其他摊主：说明加电摊位与风险时间，避免冷柜跳闸不知风险来源
        for (TempPowerRequest.NeighborLoad n : neighbors) {
            PowerApplication other = store.findByStallNo(n.getStallNo()).orElse(null);
            if (other == null) {
                continue;
            }
            notifications.push(other.getVendorUsername(), Role.VENDOR, "TEMP_PENDING",
                    box.getId(), app.getStallNo(),
                    "【加电预告】" + app.getStallNo() + " 即将加装大功率设备",
                    "同箱[" + box.getCode() + "]摊位 " + app.getStallNo() + "（" + app.getStallName()
                            + "）申请临时加装 " + t.getDeviceName() + "（" + t.getRatedPowerW() * t.getQuantity()
                            + "W），已审批通过，电工约 " + eta + " 分钟后到场接线。" + riskWindow
                            + " 为高负载风险时段，届时同箱负载率约 " + pct(t.getBoxUsageAfter())
                            + "，请您错峰使用大功率设备、留意冷柜运行；若发生跳闸或异味，风险来源为 "
                            + app.getStallNo() + "，请立即联系市场管理员/电工。",
                    LocalDateTime.now().plusMinutes(eta),
                    "今晚 " + t.getRiskUntil().toLocalTime().format(HM));
        }
        // 申请人：批准结果 + 安全提示 + 收费
        notifications.push(app.getVendorUsername(), Role.VENDOR, "TEMP_APPROVED",
                box.getId(), app.getStallNo(),
                "【加电批准】" + t.getDeviceName() + " 等待电工接线",
                "您的摊位 " + app.getStallNo() + " 临时加电已批准，加电费 " + t.getFee()
                        + " 元已计入当晚记录，电工预计 " + eta + " 分钟到场。" + tip,
                LocalDateTime.now().plusMinutes(eta),
                "今晚 " + t.getRiskUntil().toLocalTime().format(HM));
        // 收费人员：知晓收费
        User cashier = directory.duty(Role.CASHIER);
        if (cashier != null) {
            notifications.push(cashier.getUsername(), Role.CASHIER, "FEE", box.getId(), app.getStallNo(),
                    "【加电费入账】" + app.getStallNo(),
                    "摊位 " + app.getStallNo() + " 临时加电审批通过，加电费 " + t.getFee()
                            + " 元已从摊主预付账户扣除（风险时段至今晚 "
                            + t.getRiskUntil().toLocalTime().format(HM) + "）。",
                    LocalDateTime.now(), "今晚 " + t.getRiskUntil().toLocalTime().format(HM));
        }
    }

    private TempPowerRequest baseRequest(PowerApplication app, DeviceKind kind, String name,
                                         int ratedPowerW, int qty) {
        TempPowerRequest t = new TempPowerRequest("T" + store.nextId("temp"));
        t.setApplicationId(app.getId());
        t.setStallNo(app.getStallNo());
        t.setStallName(app.getStallName());
        t.setZone(app.getZone());
        t.setVendorUsername(app.getVendorUsername());
        t.setKind(kind);
        t.setDeviceName(name);
        t.setRatedPowerW(ratedPowerW);
        t.setQuantity(qty);
        t.setDiversityFactor(app.getStallType().getDiversityFactor());
        t.setAddedLoadW((int) Math.round(ratedPowerW * qty * kind.getLoadFactor()
                * app.getStallType().getDiversityFactor()));
        return t;
    }

    private PowerApplication mustOperating(String appId) {
        PowerApplication app = store.getApplication(appId);
        if (app == null) {
            throw new BizException("用电申请不存在: " + appId);
        }
        if (!Statuses.OPERATING.equals(app.getStatus())) {
            throw new BizException("摊位 " + app.getStallNo() + " 未在营业（"
                    + Statuses.label(app.getStatus()) + "），不能临时加电");
        }
        return app;
    }

    /** 可容纳新增负载的备用线路箱建议文案 */
    private String suggestBackupBox(int addedLoadW) {
        for (ElectricBox b : store.allBoxes()) {
            if (!b.isBackupLine() || "FAULT".equals(b.getStatus())) {
                continue;
            }
            if (calc.boxOperatingLoad(b.getId()) + addedLoadW <= b.safeCapacityW()) {
                return b.getCode() + "（" + b.getZone() + "，剩余约 "
                        + (b.safeCapacityW() - calc.boxOperatingLoad(b.getId())) + "W）";
            }
        }
        return null;
    }

    /** 反推：在剩余容量内，该类设备可申报的额定功率上限 */
    private int maxRatedForRemaining(PowerApplication app, DeviceKind kind, int remainingW) {
        double factor = kind.getLoadFactor() * app.getStallType().getDiversityFactor();
        if (factor <= 0) {
            return remainingW;
        }
        return (int) Math.floor(remainingW / factor);
    }

    private LocalDateTime businessClose(String hours) {
        LocalTime close = LocalTime.of(23, 59);
        if (hours != null && hours.contains("-")) {
            String tail = hours.substring(hours.indexOf('-') + 1).trim();
            try {
                close = "24:00".equals(tail) ? LocalTime.of(23, 59) : LocalTime.parse(tail, HM);
            } catch (Exception ignore) {
                // 保留默认
            }
        }
        LocalDateTime dt = LocalDateTime.of(LocalDate.now(), close);
        if (dt.isBefore(LocalDateTime.now())) {
            dt = dt.plusDays(1);
        }
        return dt;
    }

    private String directoryRole(String username) {
        User u = store.getUser(username);
        return u == null ? "" : u.getRole().name();
    }

    private String pct(double d) {
        return (int) Math.round(d * 100) + "%";
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
