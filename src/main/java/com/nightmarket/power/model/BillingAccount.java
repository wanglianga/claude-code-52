package com.nightmarket.power.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 电费预付账户：按摊主计；同一摊主多摊共用一个账户 */
@Data
@NoArgsConstructor
public class BillingAccount {
    private String vendorUsername;
    private double prepaidBalanceWan = 0;     // 预付余额（元）
    private double totalConsumedWan = 0;      // 累计电费
    private double totalTempAddFeeWan = 0;    // 累计临时加电费
    private double totalCompensationWan = 0;  // 顾客赔付/损耗承担
    private boolean lowBalance;               // 余额不足预警
    private boolean powerCutForArrears;       // 因欠费断电
    private List<LedgerEntry> ledger = new ArrayList<>();
    private LocalDateTime updatedAt;

    public BillingAccount(String vendorUsername) {
        this.vendorUsername = vendorUsername;
        this.updatedAt = LocalDateTime.now();
    }

    public void addEntry(String type, double amount, String remark) {
        ledger.add(new LedgerEntry(type, amount, remark, LocalDateTime.now()));
        this.updatedAt = LocalDateTime.now();
    }

    @Data
    @NoArgsConstructor
    public static class LedgerEntry {
        private String type;       // RECHARGE / ELECTRICITY / TEMP_ADD / COMPENSATION
        private double amount;
        private String remark;
        private LocalDateTime at;

        public LedgerEntry(String type, double amount, String remark, LocalDateTime at) {
            this.type = type;
            this.amount = amount;
            this.remark = remark;
            this.at = at;
        }
    }
}
