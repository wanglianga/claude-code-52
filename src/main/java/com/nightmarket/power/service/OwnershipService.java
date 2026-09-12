package com.nightmarket.power.service;

import com.nightmarket.power.model.*;
import com.nightmarket.power.store.RedisStore;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 商户夜间撤摊、临时摊位转让、同一摊主多摊共用设备。
 * 责任始终落在“当前责任人”身上，用电责任链不因现场混乱断开。
 */
@Service
public class OwnershipService {

    private final RedisStore store;
    private final BillingService billing;

    public OwnershipService(RedisStore store, BillingService billing) {
        this.store = store;
        this.billing = billing;
    }

    /** 夜间撤摊：记录电表终值、结算电费、断电、摊位关闭（管理员可代办） */
    public StallOwnership withdraw(String stallNo, String actor, int meterEndWh, String remark) {
        PowerApplication app = store.findByStallNo(stallNo)
                .orElseThrow(() -> new BizException("摊位 " + stallNo + " 无用电档案"));
        StallOwnership own = require(stallNo);
        if (Statuses.OPERATING.equals(app.getStatus()) || Statuses.POWER_CUT.equals(app.getStatus())) {
            billing.settleElectricity(app, meterEndWh);
        }
        app.setStatus(Statuses.CLOSED);
        store.saveApplication(app);

        own.setWithdrawn(true);
        own.setWithdrawnAt(LocalDateTime.now());
        store.saveOwnership(own);
        return own;
    }

    /** 临时转让：受让人承接用电责任与预付账户绑定（保留独立账户，责任指向受让人） */
    public StallOwnership transfer(String stallNo, String fromVendor, String toVendor, String remark) {
        StallOwnership own = require(stallNo);
        if (!own.getCurrentVendor().equalsIgnoreCase(fromVendor)) {
            throw new BizException("只有当前责任人可以转让该摊位");
        }
        if (store.getUser(toVendor) == null) {
            throw new BizException("受让摊主 " + toVendor + " 不存在");
        }
        PowerApplication app = store.findByStallNo(stallNo).orElseThrow();
        own.setCurrentVendor(toVendor);
        own.setTransferTo(toVendor);
        own.setTransferredAt(LocalDateTime.now());
        own.setTransferRemark(remark);
        store.saveOwnership(own);
        app.setVendorUsername(toVendor);
        store.saveApplication(app);
        // 确保受让人有预付账户
        billing.account(toVendor);
        return own;
    }

    /** 两个摊位共用设备登记：责任两摊连带，事件影响范围可据此追溯 */
    public StallOwnership shareDevice(String stallNo, String otherStall, String deviceDesc) {
        StallOwnership own = require(stallNo);
        StallOwnership other = require(otherStall);
        own.setSharedWithStall(otherStall);
        own.setSharedDeviceDesc(deviceDesc);
        store.saveOwnership(own);
        // 双向登记
        other.setSharedWithStall(stallNo);
        other.setSharedDeviceDesc(deviceDesc);
        store.saveOwnership(other);
        return own;
    }

    private StallOwnership require(String stallNo) {
        StallOwnership own = store.getOwnership(stallNo);
        if (own == null) {
            throw new BizException("摊位 " + stallNo + " 无归属档案");
        }
        return own;
    }
}
