package com.nightmarket.power.web;

import com.nightmarket.power.model.BillingAccount;
import com.nightmarket.power.service.BillingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 收费人员侧：电费预存、欠费巡检 */
@RestController
@RequestMapping("/api/cashier")
@PreAuthorize("hasAnyRole('CASHIER','ADMIN')")
public class CashierApiController {

    private final BillingService billing;

    public CashierApiController(BillingService billing) {
        this.billing = billing;
    }

    @PostMapping("/recharge")
    public Map<String, Object> recharge(@RequestBody Dtos.RechargeRequest req) {
        BillingAccount b = billing.recharge(req.vendor(), req.amount());
        return Map.of("success", true, "account", b);
    }

    @PostMapping("/enforce-arrears")
    public Map<String, Object> enforce() {
        return Map.of("success", true, "powerCutStalls", billing.enforceArrears());
    }
}
