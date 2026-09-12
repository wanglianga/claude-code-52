package com.nightmarket.power.service;

import com.nightmarket.power.model.*;
import com.nightmarket.power.store.RedisStore;
import org.springframework.stereotype.Service;

/**
 * 营业中临时加电：摊主加烤炉/冰柜/灯牌/音响，
 * 系统重新计算同箱负载并判断是否允许，计收临时加电费。
 */
@Service
public class TempPowerService {

    /** 临时加电费：每 100W 计收 2 元（演示费率） */
    public static final double FEE_PER_100W = 2.0;

    private final RedisStore store;
    private final PowerCalcService calc;
    private final BillingService billing;

    public TempPowerService(RedisStore store, PowerCalcService calc, BillingService billing) {
        this.store = store;
        this.calc = calc;
        this.billing = billing;
    }

    public TempPowerRequest request(String appId, String vendor, DeviceKind kind, String deviceName,
                                    int ratedPowerW, int quantity) {
        PowerApplication app = store.getApplication(appId);
        if (app == null) {
            throw new BizException("用电申请不存在: " + appId);
        }
        if (!Statuses.OPERATING.equals(app.getStatus())) {
            throw new BizException("摊位 " + app.getStallNo() + " 未在营业（"
                    + Statuses.label(app.getStatus()) + "），不能临时加电");
        }
        RiskControl risk = store.activeRiskOfStall(app.getStallNo()).orElse(null);
        if (risk != null && risk.isRestrictDevices()) {
            throw new BizException("管理员已对该摊位临时限制设备开启，暂不允许新增设备（先联系管理员解除）");
        }

        TempPowerRequest t = new TempPowerRequest("T" + store.nextId("temp"));
        t.setApplicationId(appId);
        t.setStallNo(app.getStallNo());
        t.setVendorUsername(vendor);
        t.setKind(kind);
        t.setDeviceName(deviceName);
        t.setRatedPowerW(ratedPowerW);
        t.setQuantity(quantity);

        double diversity = app.getStallType().getDiversityFactor();
        t.setDiversityFactor(diversity);
        int addedLoad = (int) Math.round(ratedPowerW * quantity * kind.getLoadFactor() * diversity);
        t.setAddedLoadW(addedLoad);

        ElectricBox box = store.getBox(app.getBoxId());
        int before = calc.boxOperatingLoad(box.getId());
        int after = before + addedLoad;
        t.setBoxLoadBeforeW(before);
        t.setBoxLoadAfterW(after);
        t.setBoxSafeCapacityW(box.safeCapacityW());

        double fee = Math.ceil(addedLoad / 100.0) * FEE_PER_100W;
        t.setFee(fee);

        if (after > box.safeCapacityW()) {
            t.setDecision(Statuses.TEMP_DENIED);
            t.setReason("同箱重算负载 " + after + "W 超过安全容量 " + box.safeCapacityW()
                    + "W（当前 " + before + "W + 本次 " + addedLoad + "W），禁止加电；"
                    + "可减载、错峰或申请迁移备用线路");
            t.setDecidedBy("SYSTEM");
            store.saveTemp(t);
            return t;
        }
        if (billing.isInsufficient(vendor, fee)) {
            t.setDecision(Statuses.TEMP_DENIED);
            t.setReason("电费预付余额不足以支付临时加电费 " + fee + " 元，请先充值");
            t.setDecidedBy("SYSTEM");
            store.saveTemp(t);
            return t;
        }
        t.setDecision(Statuses.TEMP_ALLOWED);
        t.setReason("同箱重算负载 " + after + "W / 安全容量 " + box.safeCapacityW()
                + "W，负载率 " + pct(after, box.safeCapacityW()) + "，允许加电；加电费 " + fee + " 元");
        t.setDecidedBy("SYSTEM");
        store.saveTemp(t);

        billing.chargeTempAdd(vendor, fee, app.getStallNo() + " 加装" + kind.getLabel() + "·" + deviceName);
        app.setTempAddCount(app.getTempAddCount() + 1);
        app.setTempAddPowerW(app.getTempAddPowerW() + ratedPowerW * quantity);
        app.setTempAddFee(round2(app.getTempAddFee() + fee));
        store.saveApplication(app);
        return t;
    }

    private String pct(int load, int cap) {
        return cap == 0 ? "-" : (int) Math.round(100.0 * load / cap) + "%";
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
