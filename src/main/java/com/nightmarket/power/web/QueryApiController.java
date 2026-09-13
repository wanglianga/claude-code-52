package com.nightmarket.power.web;

import com.nightmarket.power.model.*;
import com.nightmarket.power.service.PowerCalcService;
import com.nightmarket.power.store.RedisStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 只读查询接口：总览、配电箱负载、申请、事件、档案等 */
@RestController
@RequestMapping("/api")
public class QueryApiController {

    private final RedisStore store;
    private final PowerCalcService calc;

    public QueryApiController(RedisStore store, PowerCalcService calc) {
        this.store = store;
        this.calc = calc;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<PowerApplication> apps = store.allApplications();
        m.put("marketDate", store.marketDate());
        m.put("totalStalls", apps.size());
        m.put("operating", apps.stream().filter(a -> Statuses.OPERATING.equals(a.getStatus())).count());
        m.put("powerCut", apps.stream().filter(a -> Statuses.POWER_CUT.equals(a.getStatus())).count());
        m.put("suspended", apps.stream().filter(a -> Statuses.SUSPENDED.equals(a.getStatus())).count());
        m.put("pendingApproval", apps.stream().filter(a -> Statuses.PENDING.equals(a.getStatus())).count());
        m.put("openEvents", store.allEvents().stream()
                .filter(e -> Statuses.EVENT_OPEN.equals(e.getStatus())).count());
        m.put("highRiskStalls", apps.stream().filter(PowerApplication::isHighRisk)
                .map(PowerApplication::getStallNo).toList());
        m.put("boxes", calc.boxUsages().stream().map(u -> Map.of(
                "box", u.box.getCode(),
                "zone", u.box.getZone(),
                "loadW", u.operatingLoadW,
                "safeCapacityW", u.safeCapacityW,
                "usageRate", Math.round(u.usageRate * 100) + "%",
                "stalls", u.stallCount,
                "backup", u.box.isBackupLine(),
                "status", u.box.getStatus())).toList());
        return m;
    }

    @GetMapping("/applications")
    public List<PowerApplication> applications() {
        return store.allApplications();
    }

    @GetMapping("/applications/{id}")
    public Map<String, Object> applicationDetail(@org.springframework.web.bind.annotation.PathVariable String id) {
        PowerApplication app = store.getApplication(id);
        if (app == null) {
            throw new com.nightmarket.power.service.BizException("申请不存在: " + id);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("application", app);
        m.put("box", app.getBoxId() == null ? null : store.getBox(app.getBoxId()));
        m.put("connection", store.findConnectionByApp(id).orElse(null));
        m.put("tempRequests", store.tempsByApp(id));
        m.put("ownership", store.getOwnership(app.getStallNo()));
        m.put("violations", store.violationsByStall(app.getStallNo()));
        m.put("riskControl", store.activeRiskOfStall(app.getStallNo()).orElse(null));
        return m;
    }

    @GetMapping("/boxes")
    public List<PowerCalcService.BoxUsage> boxes() {
        return calc.boxUsages();
    }

    @GetMapping("/events")
    public List<IncidentEvent> events() {
        return store.allEvents();
    }

    @GetMapping("/violations")
    public List<ViolationRecord> violations() {
        return store.allViolations();
    }

    @GetMapping("/fires")
    public List<FireInspection> fires() {
        return store.allFires();
    }

    @GetMapping("/risks")
    public List<RiskControl> risks() {
        return store.allRisks();
    }

    @GetMapping("/temps")
    public List<TempPowerRequest> temps() {
        return store.allTemps();
    }

    @GetMapping("/billing")
    public List<BillingAccount> billing() {
        return store.allBillings();
    }

    @GetMapping("/ownership")
    public List<StallOwnership> ownership() {
        return store.allOwnerships();
    }

    @GetMapping("/archives")
    public List<StallArchive> archives() {
        return store.allArchives();
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        return store.allUsers().stream()
                .map(u -> Map.<String, Object>of(
                        "username", u.getUsername(),
                        "name", u.getName(),
                        "role", u.getRole().name(),
                        "phone", u.getPhone() == null ? "" : u.getPhone()))
                .toList();
    }

    /** 当前登录人的站内通知（加电预告/批准/生效、接线任务、收费提醒） */
    @GetMapping("/notifications/mine")
    public List<Notification> myNotifications() {
        return store.notificationsByVendor(CurrentUser.name());
    }

    @org.springframework.web.bind.annotation.PostMapping("/notifications/{id}/read")
    public Map<String, Object> markRead(@org.springframework.web.bind.annotation.PathVariable String id) {
        Notification n = store.getNotification(id);
        if (n != null && CurrentUser.name().equalsIgnoreCase(n.getTargetVendor())) {
            n.setRead(true);
            store.saveNotification(n);
        }
        return Map.of("success", true);
    }
}
