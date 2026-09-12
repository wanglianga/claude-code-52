package com.nightmarket.power.service;

import com.nightmarket.power.model.*;
import com.nightmarket.power.store.RedisStore;
import org.springframework.stereotype.Service;

/** 电工现场接线：插座编号、漏保状态、电表初值、线缆照片、摊主签字 */
@Service
public class ConnectionService {

    private final RedisStore store;

    public ConnectionService(RedisStore store) {
        this.store = store;
    }

    public ConnectionRecord connect(String appId, String electrician, String socketNo, String rcdStatus,
                                    int meterInitial, String cablePhoto, String signature,
                                    boolean vendorSigned, String remark) {
        PowerApplication app = mustApproved(appId);
        RiskControl risk = store.activeRiskOfStall(app.getStallNo()).orElse(null);
        if (risk != null && risk.isRequireRecheck() && !risk.isRecheckPassed()) {
            throw new BizException("该摊位处于高风险复检状态，须电工复检通过后方可接线送电");
        }
        if (!vendorSigned) {
            throw new BizException("摊主未签字确认，不能送电");
        }
        ConnectionRecord rec = new ConnectionRecord("C" + store.nextId("conn"), appId, app.getStallNo());
        rec.setElectricianUsername(electrician);
        rec.setSocketNo(socketNo);
        rec.setRcdStatus(rcdStatus);
        rec.setMeterInitialWh(meterInitial);
        rec.setCablePhotoUrl(cablePhoto);
        rec.setSignatureUrl(signature);
        rec.setVendorSigned(true);
        rec.setRemark(remark);
        store.saveConnection(rec);

        app.setStatus(Statuses.OPERATING);
        app.setMeterStartWh(meterInitial);
        store.saveApplication(app);
        return rec;
    }

    public PowerApplication mustApproved(String appId) {
        PowerApplication app = store.getApplication(appId);
        if (app == null) {
            throw new BizException("用电申请不存在: " + appId);
        }
        if (!Statuses.APPROVED.equals(app.getStatus())) {
            throw new BizException("摊位 " + app.getStallNo() + " 当前状态["
                    + Statuses.label(app.getStatus()) + "]，不可接线（须为已批准·待接线）");
        }
        return app;
    }
}
