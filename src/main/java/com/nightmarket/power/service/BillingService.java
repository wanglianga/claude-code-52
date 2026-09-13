package com.nightmarket.power.service;

import com.nightmarket.power.model.BillingAccount;
import com.nightmarket.power.model.Statuses;
import com.nightmarket.power.model.PowerApplication;
import com.nightmarket.power.store.RedisStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 电费预付与收费：一个摊主一个账户（同一摊主多摊共用）。
 * 预付不足触发预警，持续不足可断电；临时加电费、电费、赔付都走同一本账。
 */
@Service
public class BillingService {

    /** 电价：1.5 元/度（演示） */
    public static final double PRICE_PER_KWH = 1.5;
    /** 低余额阈值 */
    public static final double LOW_BALANCE = 10.0;

    private final RedisStore store;

    public BillingService(RedisStore store) {
        this.store = store;
    }

    public BillingAccount account(String vendor) {
        BillingAccount b = store.getBilling(vendor);
        if (b == null) {
            b = new BillingAccount(vendor);
            store.saveBilling(b);
        }
        return b;
    }

    public BillingAccount recharge(String vendor, double amount) {
        if (amount <= 0) {
            throw new BizException("充值金额必须大于 0");
        }
        BillingAccount b = account(vendor);
        b.setPrepaidBalanceWan(round2(b.getPrepaidBalanceWan() + amount));
        b.addEntry("RECHARGE", amount, "电费预存充值");
        b.setLowBalance(b.getPrepaidBalanceWan() < LOW_BALANCE);
        if (b.getPrepaidBalanceWan() >= LOW_BALANCE) {
            b.setPowerCutForArrears(false);
        }
        store.saveBilling(b);
        return b;
    }

    /** 判断余额是否足够（不实际扣款） */
    public boolean isInsufficient(String vendor, double need) {
        return account(vendor).getPrepaidBalanceWan() < need;
    }

    public void chargeTempAdd(String vendor, double fee, String remark) {
        BillingAccount b = account(vendor);
        b.setPrepaidBalanceWan(round2(b.getPrepaidBalanceWan() - fee));
        b.setTotalTempAddFeeWan(round2(b.getTotalTempAddFeeWan() + fee));
        b.addEntry("TEMP_ADD", -fee, remark);
        b.setLowBalance(b.getPrepaidBalanceWan() < LOW_BALANCE);
        store.saveBilling(b);
    }

    /** 审批通过已计费、但电工到场复核无法接线时原路退还加电费 */
    public void refundTempAdd(String vendor, double fee, String remark) {
        if (fee <= 0) {
            return;
        }
        BillingAccount b = account(vendor);
        b.setPrepaidBalanceWan(round2(b.getPrepaidBalanceWan() + fee));
        b.setTotalTempAddFeeWan(round2(Math.max(0, b.getTotalTempAddFeeWan() - fee)));
        b.addEntry("TEMP_ADD_REFUND", fee, remark);
        b.setLowBalance(b.getPrepaidBalanceWan() < LOW_BALANCE);
        store.saveBilling(b);
    }

    /** 闭市结算电表用电量并扣费 */
    public double settleElectricity(PowerApplication app, int meterEndWh) {
        int start = app.getMeterStartWh();
        int consumed = Math.max(0, meterEndWh - start);
        double fee = round2(consumed / 1000.0 * PRICE_PER_KWH);
        BillingAccount b = account(app.getVendorUsername());
        b.setPrepaidBalanceWan(round2(b.getPrepaidBalanceWan() - fee));
        b.setTotalConsumedWan(round2(b.getTotalConsumedWan() + fee));
        b.addEntry("ELECTRICITY", -fee,
                "摊位 " + app.getStallNo() + " 用电 " + consumed + "Wh 电费结算");
        b.setLowBalance(b.getPrepaidBalanceWan() < LOW_BALANCE);
        store.saveBilling(b);

        app.setMeterEndWh(meterEndWh);
        app.setConsumedWh(consumed);
        app.setElectricityFee(fee);
        store.saveApplication(app);
        return fee;
    }

    /** 事件中的顾客赔付/食品损耗分摊 */
    public void chargeCompensation(String vendor, double amount, String remark) {
        if (amount <= 0) {
            return;
        }
        BillingAccount b = account(vendor);
        b.setPrepaidBalanceWan(round2(b.getPrepaidBalanceWan() - amount));
        b.setTotalCompensationWan(round2(b.getTotalCompensationWan() + amount));
        b.addEntry("COMPENSATION", -amount, remark);
        b.setLowBalance(b.getPrepaidBalanceWan() < LOW_BALANCE);
        store.saveBilling(b);
    }

    /** 收费调整（风控加收/减免，金额带符号） */
    public void feeAdjustment(String vendor, double amount, String remark) {
        BillingAccount b = account(vendor);
        b.setPrepaidBalanceWan(round2(b.getPrepaidBalanceWan() + amount));
        b.addEntry("FEE_ADJUST", amount, remark);
        b.setLowBalance(b.getPrepaidBalanceWan() < LOW_BALANCE);
        store.saveBilling(b);
    }

    /**
     * 巡检预付余额：不足则预警并对其在营摊位断电（责任链上通知），
     * 返回被处理的摊位。
     */
    public java.util.List<String> enforceArrears() {
        List<String> cut = new java.util.ArrayList<>();
        for (BillingAccount b : store.allBillings()) {
            if (b.getPrepaidBalanceWan() < 0) {
                b.setLowBalance(true);
                b.setPowerCutForArrears(true);
                store.saveBilling(b);
                for (PowerApplication app : store.findByVendor(b.getVendorUsername())) {
                    if (Statuses.OPERATING.equals(app.getStatus())) {
                        app.setStatus(Statuses.POWER_CUT);
                        store.saveApplication(app);
                        cut.add(app.getStallNo());
                    }
                }
            }
        }
        return cut;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
