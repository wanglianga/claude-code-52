package com.nightmarket.power.web;

import com.nightmarket.power.model.FireInspection;
import com.nightmarket.power.model.InspectionResult;
import com.nightmarket.power.service.FireInspectionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 消防部门侧：夜间用电消防抽检 */
@RestController
@RequestMapping("/api/fire")
@PreAuthorize("hasAnyRole('FIREFIGHTER','ADMIN')")
public class FireApiController {

    private final FireInspectionService fireInspectionService;

    public FireApiController(FireInspectionService fireInspectionService) {
        this.fireInspectionService = fireInspectionService;
    }

    @PostMapping("/inspections")
    public Map<String, Object> inspect(@RequestBody Dtos.FireInspectionRequest req) {
        FireInspection f = fireInspectionService.inspect(req.stallNo(), CurrentUser.name(),
                InspectionResult.fromCode(req.result()), req.findings(),
                req.points() == null ? 0 : req.points(), req.rectifyPhotoUrl());
        return Map.of("success", true, "inspection", f);
    }
}
