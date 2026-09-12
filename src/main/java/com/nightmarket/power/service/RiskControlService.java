package com.nightmarket.power.service;

import com.nightmarket.power.model.*;
import com.nightmarket.power.store.RedisStore;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 高风险摊位风控：管理员临时限制设备开启、要求电工复检、迁到备用线路；
 * 市场广播与收费调整同步记录在同一条风控措施上。
 */
@Service
public class RiskControlService {

    private final RedisStore store;
    private final ApplicationService applicationService;
    private final BillingService billing;

    public RiskControlService(RedisStore store, ApplicationService applicationService, BillingService billing) {
        this.store = store;
        this.applicationService = applicationService;
        this.billing = billing;
    }

    public RiskControl apply(String stallNo, String admin, boolean restrict, String restrictedCsv,
                             boolean requireRecheck, boolean migrate, String targetBoxId,
                             String broadcast, double feeAdjust, String reason) {
        PowerApplication app = applicationService.mustGetByStall(stallNo);
        RiskControl r = new RiskControl("R" + store.nextId("risk"));
        r.setStallNo(stallNo);
        r.setAdminUsername(admin);
        r.setRestrictDevices(restrict);
        if (restrict && restrictedCsv != null && !restrictedCsv.isBlank()) {
            r.getRestrictedDevices().addAll(Arrays.stream(restrictedCsv.split("[,，]"))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList());
        }
        r.setRequireRecheck(requireRecheck);
        r.setMigrateLine(migrate);
        r.setBroadcast(broadcast);
        r.setFeeAdjustmentWan(feeAdjust);
        r.setReason(reason);

        if (migrate) {
            ElectricBox target = store.getBox(targetBoxId);
            if (target == null || !target.isBackupLine()) {
                throw new BizException("迁移目标必须是备用线路配电箱");
            }
            r.setTargetBoxId(targetBoxId);
            applicationService.reassignBox(app.getId(), targetBoxId);
        }
        store.saveRisk(r);

        // 限制设备开启 / 要求复检期间：在营摊位立即暂停供电，待复检通过恢复
        if ((restrict || requireRecheck) && Statuses.OPERATING.equals(app.getStatus())) {
            app.setStatus(Statuses.POWER_CUT);
            store.saveApplication(app);
        }

        // 收费调整同步入账（正=减免返还，负=加收）
        if (feeAdjust != 0) {
            billing.feeAdjustment(app.getVendorUsername(), feeAdjust,
                    "摊位 " + stallNo + " 风控收费调整：" + reason);
        }
        return r;
    }

    /** 电工复检结论 */
    public RiskControl recheck(String riskId, String electrician, boolean passed, String note) {
        RiskControl r = store.allRisks().stream()
                .filter(x -> x.getId().equals(riskId)).findFirst()
                .orElseThrow(() -> new BizException("风控措施不存在: " + riskId));
        r.setRecheckElectrician(electrician);
        r.setRecheckPassed(passed);
        PowerApplication app = applicationService.mustGetByStall(r.getStallNo());
        if (passed && Statuses.POWER_CUT.equals(app.getStatus())) {
            // 复检通过且没有未结事件，恢复营业
            boolean openEvent = store.allEvents().stream()
                    .anyMatch(e -> e.getPrimaryStallNo().equalsIgnoreCase(r.getStallNo())
                            && Statuses.EVENT_OPEN.equals(e.getStatus()));
            if (!openEvent) {
                app.setStatus(Statuses.OPERATING);
                store.saveApplication(app);
            }
        }
        store.saveRisk(r);
        return r;
    }

    /** 解除风控（管理员） */
    public RiskControl lift(String riskId, String admin) {
        RiskControl r = store.allRisks().stream()
                .filter(x -> x.getId().equals(riskId)).findFirst()
                .orElseThrow(() -> new BizException("风控措施不存在: " + riskId));
        r.setStatus("LIFTED");
        r.setLiftedAt(java.time.LocalDateTime.now());
        store.saveRisk(r);
        PowerApplication app = applicationService.mustGetByStall(r.getStallNo());
        if (Statuses.POWER_CUT.equals(app.getStatus())) {
            app.setStatus(Statuses.OPERATING);
            store.saveApplication(app);
        }
        return r;
    }

    public List<RiskControl> list() {
        return store.allRisks();
    }
}
