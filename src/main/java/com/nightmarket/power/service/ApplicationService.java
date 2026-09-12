package com.nightmarket.power.service;

import com.nightmarket.power.model.*;
import com.nightmarket.power.store.RedisStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用电申请与审批：
 * 系统根据摊位位置、配电箱容量、线路负载、历史违规、同区域高峰自动生成审批；
 * 高风险申请转管理员复核。
 */
@Service
public class ApplicationService {

    private final RedisStore store;
    private final PowerCalcService calc;

    public ApplicationService(RedisStore store, PowerCalcService calc) {
        this.store = store;
        this.calc = calc;
    }

    public PowerApplication submit(String stallNo, String stallName, String vendor, String zone,
                                   StallType type, List<Device> devices, boolean openFlame,
                                   boolean freezerNeeded, boolean lightingNeeded, String hours) {
        if (store.findByStallNo(stallNo).isPresent()) {
            throw new BizException("摊位号 " + stallNo + " 已存在申请");
        }
        PowerApplication app = new PowerApplication("A" + store.nextId("app"));
        app.setStallNo(stallNo);
        app.setStallName(stallName);
        app.setVendorUsername(vendor);
        app.setZone(zone);
        app.setStallType(type);
        app.setDevices(devices);
        app.setOpenFlame(openFlame);
        app.setFreezerNeeded(freezerNeeded);
        app.setLightingNeeded(lightingNeeded);
        app.setBusinessHours(hours);
        app.setDeclaredTotalW(calc.declaredTotal(app));
        store.saveApplication(app);

        StallOwnership own = new StallOwnership(stallNo, vendor);
        store.saveOwnership(own);

        evaluate(app);
        return app;
    }

    /** 系统自动生成审批意见 */
    public PowerApplication evaluate(PowerApplication app) {
        ElectricBox box = calc.selectBox(app);
        if (box == null) {
            app.setStatus(Statuses.REJECTED);
            app.setDecisionBy("SYSTEM");
            app.setRiskScore(0);
            app.setDecisionRemark("所在区域配电箱均无足够安全容量承载该申请（"
                    + calc.effectiveLoad(app) + "W），请减载或改申请备用线路");
            store.saveApplication(app);
            return app;
        }
        app.setBoxId(box.getId());
        PowerCalcService.RiskAssessment ra = calc.assess(app, box);
        app.setRiskScore(ra.score);
        app.setRiskFactors(ra.factors);
        app.setHighRisk(ra.highRisk);
        app.setApprovedPowerW(calc.effectiveLoad(app));
        app.setEstimatedBoxLoadW(calc.boxProjectedLoad(box.getId(), app));

        if (ra.highRisk) {
            app.setStatus(Statuses.PENDING);
            app.setDecisionBy("SYSTEM");
            app.setDecisionRemark("系统初审：风险分 " + ra.score + " 达高风险线，需管理员复核后决定（可附加风控措施）");
        } else {
            app.setStatus(Statuses.APPROVED);
            app.setDecisionBy("SYSTEM");
            app.setDecisionRemark("系统自动批准：拟接[" + box.getCode() + "]，核定负载 "
                    + app.getApprovedPowerW() + "W，审批后箱负载率 "
                    + pct(app.getEstimatedBoxLoadW(), box.safeCapacityW()));
            app.setDecisionAt(java.time.LocalDateTime.now());
        }
        store.saveApplication(app);
        return app;
    }

    public PowerApplication adminApprove(String appId, String admin, String remark) {
        PowerApplication app = mustGet(appId);
        // 复核时再算一次容量，防止情况变化
        ElectricBox box = store.getBox(app.getBoxId());
        int projected = calc.boxProjectedLoad(box.getId(), app);
        if (projected > box.safeCapacityW()) {
            throw new BizException("配电箱[" + box.getCode() + "]当前安全容量不足（预测 "
                    + projected + "W / 上限 " + box.safeCapacityW() + "W），无法批准");
        }
        app.setStatus(Statuses.APPROVED);
        app.setDecisionBy(admin);
        app.setDecisionAt(java.time.LocalDateTime.now());
        app.setEstimatedBoxLoadW(projected);
        app.setDecisionRemark("管理员复核批准：" + (remark == null || remark.isBlank() ? "同意送电" : remark)
                + "；拟接[" + box.getCode() + "]，核定负载 " + app.getApprovedPowerW() + "W");
        store.saveApplication(app);
        return app;
    }

    public PowerApplication adminReject(String appId, String admin, String remark) {
        PowerApplication app = mustGet(appId);
        app.setStatus(Statuses.REJECTED);
        app.setDecisionBy(admin);
        app.setDecisionAt(java.time.LocalDateTime.now());
        app.setDecisionRemark("管理员驳回：" + remark);
        store.saveApplication(app);
        return app;
    }

    /** 为高风险摊位迁移指定备用箱（风控调用后重算） */
    public void reassignBox(String appId, String boxId) {
        PowerApplication app = mustGet(appId);
        ElectricBox box = store.getBox(boxId);
        if (box == null) {
            throw new BizException("配电箱不存在");
        }
        app.setBoxId(boxId);
        app.setEstimatedBoxLoadW(calc.boxProjectedLoad(boxId, app));
        store.saveApplication(app);
    }

    public PowerApplication mustGet(String appId) {
        PowerApplication app = store.getApplication(appId);
        if (app == null) {
            throw new BizException("用电申请不存在: " + appId);
        }
        return app;
    }

    public PowerApplication mustGetByStall(String stallNo) {
        return store.findByStallNo(stallNo)
                .orElseThrow(() -> new BizException("摊位 " + stallNo + " 无用电申请"));
    }

    private String pct(int load, int cap) {
        return cap == 0 ? "-" : (int) Math.round(100.0 * load / cap) + "%";
    }
}
