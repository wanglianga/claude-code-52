package com.nightmarket.power.web;

import com.nightmarket.power.model.*;
import com.nightmarket.power.service.ApplicationService;
import com.nightmarket.power.service.BillingService;
import com.nightmarket.power.service.TempPowerService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 摊主侧：报名提交用电申请、营业中临时加电、预存电费、查询本人摊位 */
@RestController
@RequestMapping("/api/vendor")
@PreAuthorize("hasAnyRole('VENDOR','ADMIN')")
public class VendorApiController {

    private final ApplicationService applicationService;
    private final TempPowerService tempPowerService;
    private final BillingService billing;
    private final com.nightmarket.power.store.RedisStore store;

    public VendorApiController(ApplicationService applicationService, TempPowerService tempPowerService,
                               BillingService billing, com.nightmarket.power.store.RedisStore store) {
        this.applicationService = applicationService;
        this.tempPowerService = tempPowerService;
        this.billing = billing;
        this.store = store;
    }

    /** 摊主报名提交用电申请，服务即时生成审批 */
    @PostMapping("/applications")
    public Map<String, Object> submit(@RequestBody Dtos.ApplicationRequest req) {
        java.util.List<Device> devices = req.devices() == null ? java.util.List.of() :
                req.devices().stream().map(d -> new Device(
                        DeviceKind.fromCode(d.kind()),
                        d.name(),
                        d.ratedPowerW(),
                        d.quantity() == null ? 1 : d.quantity())).toList();
        PowerApplication app = applicationService.submit(
                req.stallNo(), req.stallName(), CurrentUser.name(), req.zone(),
                StallType.valueOf(req.stallType().trim().toUpperCase()), devices,
                req.openFlame(), req.freezerNeeded(), req.lightingNeeded(), req.businessHours());
        return Map.of("success", true, "application", app,
                "decision", app.getStatus(), "remark", app.getDecisionRemark());
    }

    /** 营业中临时增加烤炉/冰柜/灯牌/音响：自动重算同箱负载 */
    @PostMapping("/temp-power")
    public Map<String, Object> tempPower(@RequestBody Dtos.TempPowerRequest req) {
        TempPowerRequest t = tempPowerService.request(
                req.applicationId(), CurrentUser.name(),
                DeviceKind.fromCode(req.kind()), req.deviceName(),
                req.ratedPowerW(), req.quantity() == null ? 1 : req.quantity());
        return Map.of("success", Statuses.TEMP_ALLOWED.equals(t.getDecision()),
                "decision", t.getDecision(), "request", t, "reason", t.getReason());
    }

    /** 预存电费（摊主给自己充值；ADMIN 可代充，body 中指定 vendor） */
    @PostMapping("/billing/recharge")
    public Map<String, Object> recharge(@RequestBody Dtos.RechargeRequest req) {
        String vendor = CurrentUser.is("ADMIN") ? req.vendor() : CurrentUser.name();
        BillingAccount b = billing.recharge(vendor, req.amount());
        return Map.of("success", true, "account", b);
    }

    @GetMapping("/me")
    public Object me() {
        if (CurrentUser.is("ADMIN")) {
            return store.allApplications();
        }
        return store.findByVendor(CurrentUser.name());
    }
}
