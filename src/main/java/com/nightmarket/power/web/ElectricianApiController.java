package com.nightmarket.power.web;

import com.nightmarket.power.model.*;
import com.nightmarket.power.service.ConnectionService;
import com.nightmarket.power.service.IncidentService;
import com.nightmarket.power.service.RiskControlService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 电工侧：现场接线、故障点确认与整改、高风险复检 */
@RestController
@RequestMapping("/api/electrician")
@PreAuthorize("hasRole('ELECTRICIAN')")
public class ElectricianApiController {

    private final ConnectionService connectionService;
    private final IncidentService incidentService;
    private final RiskControlService riskControlService;

    public ElectricianApiController(ConnectionService connectionService, IncidentService incidentService,
                                    RiskControlService riskControlService) {
        this.connectionService = connectionService;
        this.incidentService = incidentService;
        this.riskControlService = riskControlService;
    }

    /** 现场接线送电：插座编号、漏保、电表初值、线缆照片、摊主签字 */
    @PostMapping("/connections")
    public Map<String, Object> connect(@RequestBody Dtos.ConnectionRequest req) {
        ConnectionRecord rec = connectionService.connect(
                req.applicationId(), CurrentUser.name(), req.socketNo(), req.rcdStatus(),
                req.meterInitialWh(), req.cablePhotoUrl(), req.signatureUrl(), true, req.remark());
        return Map.of("success", true, "connection", rec);
    }

    /** 确认故障点、整改情况与整改照片（恢复供电前置条件） */
    @PostMapping("/events/{id}/fault")
    public Map<String, Object> confirmFault(@PathVariable String id, @RequestBody Dtos.FaultRequest req) {
        IncidentEvent e = incidentService.confirmFault(
                id, CurrentUser.name(), req.faultPoint(), req.rectified(), req.rectifyPhotoUrl());
        return Map.of("success", true, "event", e);
    }

    /** 高风险摊位管理员要求复检后的电工复检结论 */
    @PostMapping("/risks/{id}/recheck")
    public Map<String, Object> recheck(@PathVariable String id, @RequestBody Dtos.RecheckRequest req) {
        RiskControl r = riskControlService.recheck(id, CurrentUser.name(), req.passed(), req.note());
        return Map.of("success", true, "riskControl", r);
    }

    /** 电工恢复送电 */
    @PostMapping("/events/{id}/recover")
    public Map<String, Object> recover(@PathVariable String id) {
        IncidentEvent e = incidentService.recover(id, CurrentUser.name(), Role.ELECTRICIAN);
        return Map.of("success", true, "event", e);
    }
}
