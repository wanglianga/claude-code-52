package com.nightmarket.power.web;

import com.nightmarket.power.model.*;
import com.nightmarket.power.service.*;
import com.nightmarket.power.store.RedisStore;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 市场管理员侧：审批复核、事件责任认定与恢复、风控、摊位变动、闭市归档 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminApiController {

    private final ApplicationService applicationService;
    private final IncidentService incidentService;
    private final RiskControlService riskControlService;
    private final OwnershipService ownershipService;
    private final ArchiveService archiveService;
    private final BillingService billing;
    private final RedisStore store;

    public AdminApiController(ApplicationService applicationService, IncidentService incidentService,
                              RiskControlService riskControlService, OwnershipService ownershipService,
                              ArchiveService archiveService, BillingService billing, RedisStore store) {
        this.applicationService = applicationService;
        this.incidentService = incidentService;
        this.riskControlService = riskControlService;
        this.ownershipService = ownershipService;
        this.archiveService = archiveService;
        this.billing = billing;
        this.store = store;
    }

    @PostMapping("/applications/{id}/approve")
    public Map<String, Object> approve(@PathVariable String id, @RequestBody(required = false) Dtos.DecisionRequest req) {
        PowerApplication app = applicationService.adminApprove(id, CurrentUser.name(),
                req == null ? "" : req.remark());
        return Map.of("success", true, "application", app);
    }

    @PostMapping("/applications/{id}/reject")
    public Map<String, Object> reject(@PathVariable String id, @RequestBody Dtos.DecisionRequest req) {
        PowerApplication app = applicationService.adminReject(id, CurrentUser.name(), req.remark());
        return Map.of("success", true, "application", app);
    }

    /** 报告跳闸/发热/私拉/水浸/异味（管理员也可首报） */
    @PostMapping("/incidents")
    public Map<String, Object> report(@RequestBody Dtos.IncidentReportRequest req) {
        IncidentEvent e = incidentService.report(IncidentType.fromCode(req.type()), req.stallNo(),
                CurrentUser.name(), Role.ADMIN, req.description(),
                req.severity() == null ? 3 : req.severity());
        return Map.of("success", true, "event", e);
    }

    /** 恢复供电前核查 + 责任认定：故障点、受影响摊位、食品损耗、顾客赔付、暂停营业 */
    @PostMapping("/incidents/{id}/resolve")
    public Map<String, Object> resolve(@PathVariable String id, @RequestBody Dtos.ResolveRequest req) {
        IncidentEvent e = incidentService.resolve(id, CurrentUser.name(),
                Responsibility.fromCode(req.responsibility()), req.points(),
                req.foodLoss(), req.customerCompensation(), req.suspendStall(),
                req.liabilityRemark(), req.recoverNow());
        return Map.of("success", true, "event", e);
    }

    @PostMapping("/incidents/{id}/recover")
    public Map<String, Object> recover(@PathVariable String id) {
        return Map.of("success", true, "event", incidentService.recover(id, CurrentUser.name(), Role.ADMIN));
    }

    @PostMapping("/incidents/{id}/close")
    public Map<String, Object> close(@PathVariable String id) {
        return Map.of("success", true, "event", incidentService.close(id, CurrentUser.name()));
    }

    /** 高风险摊位风控：限制设备 / 电工复检 / 迁备用线路，同步广播与收费调整 */
    @PostMapping("/risks")
    public Map<String, Object> risk(@RequestBody Dtos.RiskRequest req) {
        RiskControl r = riskControlService.apply(req.stallNo(), CurrentUser.name(),
                req.restrictDevices(), req.restrictedDevices(), req.requireRecheck(),
                req.migrateLine(), req.targetBoxId(), req.broadcast(),
                req.feeAdjustment(), req.reason());
        return Map.of("success", true, "riskControl", r);
    }

    @PostMapping("/risks/{id}/lift")
    public Map<String, Object> liftRisk(@PathVariable String id) {
        return Map.of("success", true, "riskControl", riskControlService.lift(id, CurrentUser.name()));
    }

    /** 代办夜间撤摊 */
    @PostMapping("/stalls/withdraw")
    public Map<String, Object> withdraw(@RequestBody Dtos.WithdrawRequest req) {
        return Map.of("success", true,
                "ownership", ownershipService.withdraw(req.stallNo(), CurrentUser.name(),
                        req.meterEndWh(), req.remark()));
    }

    /** 临时摊位转让（管理员见证生效，责任链切到受让方） */
    @PostMapping("/stalls/transfer")
    public Map<String, Object> transfer(@RequestBody Dtos.TransferRequest req) {
        PowerApplication app = applicationService.mustGetByStall(req.stallNo());
        return Map.of("success", true,
                "ownership", ownershipService.transfer(req.stallNo(), app.getVendorUsername(),
                        req.toVendor(), req.remark()));
    }

    /** 同一摊主多摊 / 两摊共用设备登记 */
    @PostMapping("/stalls/share-device")
    public Map<String, Object> share(@RequestBody Dtos.ShareRequest req) {
        return Map.of("success", true,
                "ownership", ownershipService.shareDevice(req.stallNo(), req.otherStall(), req.deviceDesc()));
    }

    /** 预付不足巡检：欠费摊位断电 */
    @PostMapping("/billing/enforce-arrears")
    public Map<String, Object> enforce() {
        List<String> cut = billing.enforceArrears();
        return Map.of("success", true, "powerCutStalls", cut);
    }

    /** 单摊归档 */
    @PostMapping("/archives/{stallNo}")
    public Map<String, Object> archive(@PathVariable String stallNo,
                                       @RequestBody(required = false) Map<String, Integer> body) {
        PowerApplication app = applicationService.mustGetByStall(stallNo);
        int end = body == null || body.get("meterEndWh") == null ? app.getMeterEndWh() : body.get("meterEndWh");
        return Map.of("success", true, "archive", archiveService.archive(app, end));
    }

    /** 夜市闭市：统一读表结算归档，生成下期分配与评级 */
    @PostMapping("/close-market")
    public Map<String, Object> closeMarket(@RequestBody(required = false) Map<String, Integer> body) {
        int end = body == null || body.get("meterEndWh") == null ? 5000 : body.get("meterEndWh");
        List<StallArchive> arcs = archiveService.closeMarket(end);
        return Map.of("success", true, "archived", arcs.size(), "archives", arcs);
    }
}
