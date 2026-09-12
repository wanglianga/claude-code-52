package com.nightmarket.power.web;

import com.nightmarket.power.model.IncidentEvent;
import com.nightmarket.power.model.IncidentType;
import com.nightmarket.power.model.Role;
import com.nightmarket.power.service.IncidentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 安保侧：首报险情、到场处置留痕 */
@RestController
@RequestMapping("/api/security")
@PreAuthorize("hasAnyRole('SECURITY','ADMIN')")
public class SecurityApiController {

    private final IncidentService incidentService;

    public SecurityApiController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping("/incidents")
    public Map<String, Object> report(@RequestBody Dtos.IncidentReportRequest req) {
        IncidentEvent e = incidentService.report(IncidentType.fromCode(req.type()), req.stallNo(),
                CurrentUser.name(), Role.SECURITY, req.description(),
                req.severity() == null ? 3 : req.severity());
        return Map.of("success", true, "event", e);
    }

    @PostMapping("/events/{id}/dispatch")
    public Map<String, Object> dispatch(@PathVariable String id, @RequestBody Dtos.DispatchRequest req) {
        IncidentEvent e = incidentService.securityDispatch(id, CurrentUser.name(), req.action());
        return Map.of("success", true, "event", e);
    }
}
