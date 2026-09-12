package com.nightmarket.power.service;

import com.nightmarket.power.model.*;
import com.nightmarket.power.store.RedisStore;
import org.springframework.stereotype.Service;

/** 消防部门抽检：合格 / 限期整改 / 不合格立即停业，结果进违规与摊位档案 */
@Service
public class FireInspectionService {

    private final RedisStore store;

    public FireInspectionService(RedisStore store) {
        this.store = store;
    }

    public FireInspection inspect(String stallNo, String firefighter, InspectionResult result,
                                  String findings, int points, String rectifyPhoto) {
        PowerApplication app = store.findByStallNo(stallNo)
                .orElseThrow(() -> new BizException("摊位 " + stallNo + " 无用电档案"));
        FireInspection f = new FireInspection("F" + store.nextId("fire"));
        f.setStallNo(stallNo);
        f.setFirefighterUsername(firefighter);
        f.setResult(result);
        f.setFindings(findings);
        f.setDeductPoints(points);
        f.setRectifyPhotoUrl(rectifyPhoto);
        store.saveFire(f);

        if (result != InspectionResult.PASS) {
            ViolationRecord v = new ViolationRecord("V" + store.nextId("violation"));
            v.setStallNo(stallNo);
            v.setVendorUsername(app.getVendorUsername());
            v.setType(IncidentType.FIRE_HAZARD);
            v.setPoints(points);
            v.setResponsibility(Responsibility.VENDOR);
            v.setDetail("消防抽检[" + result.getLabel() + "]：" + findings);
            v.setRecorder(firefighter);
            v.setRecorderRole(Role.FIREFIGHTER);
            v.setRectifyPhotoUrl(rectifyPhoto);
            store.saveViolation(v);
        }
        if (result == InspectionResult.FAIL) {
            app.setStatus(Statuses.SUSPENDED);
            store.saveApplication(app);
        }
        return f;
    }
}
