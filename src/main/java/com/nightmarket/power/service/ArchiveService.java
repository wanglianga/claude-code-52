package com.nightmarket.power.service;

import com.nightmarket.power.model.*;
import com.nightmarket.power.store.RedisStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 夜市闭市结算与安全评级：
 * 用电量、违规扣分、临时加电费、跳闸责任、整改照片进入摊位档案，
 * 用于下期摊位分配与安全评级（A/B/C/D）。
 */
@Service
public class ArchiveService {

    private final RedisStore store;
    private final BillingService billing;

    public ArchiveService(RedisStore store, BillingService billing) {
        this.store = store;
        this.billing = billing;
    }

    /** 单摊归档（撤摊时/管理员手动）。meterEndWh<=0 表示沿用已结算读数；从未接线的摊位只入档不计电费。 */
    public StallArchive archive(PowerApplication app, int meterEndWh) {
        boolean connected = store.findConnectionByApp(app.getId()).isPresent();
        if (connected && meterEndWh > 0 && app.getMeterEndWh() == 0) {
            billing.settleElectricity(app, meterEndWh);
        }
        List<ViolationRecord> vrs = store.violationsByStall(app.getStallNo());
        int points = vrs.stream().mapToInt(ViolationRecord::getPoints).sum();

        List<IncidentEvent> events = store.allEvents().stream()
                .filter(e -> app.getStallNo().equalsIgnoreCase(e.getPrimaryStallNo())).toList();

        StallArchive arc = new StallArchive("AR" + store.nextId("archive"));
        arc.setStallNo(app.getStallNo());
        arc.setStallName(app.getStallName());
        arc.setVendorUsername(app.getVendorUsername());
        arc.setZone(app.getZone());
        arc.setStallType(app.getStallType());
        arc.setMeterStartWh(app.getMeterStartWh());
        arc.setMeterEndWh(app.getMeterEndWh());
        arc.setConsumedWh(app.getConsumedWh());
        arc.setElectricityFeeWan(app.getElectricityFee());
        arc.setViolationPoints(points);
        for (ViolationRecord v : vrs) {
            arc.getViolations().add("[" + v.getType().getLabel() + "]" + v.getPoints() + "分/"
                    + v.getResponsibility().getLabel() + "：" + v.getDetail());
        }
        arc.setTempAddCount(app.getTempAddCount());
        arc.setTempAddFeeWan(app.getTempAddFee());
        arc.setIncidentCount(events.size());
        for (IncidentEvent e : events) {
            arc.getIncidentResponsibilities().add(
                    e.getType().getLabel() + "→" + (e.getResponsibility() == null ? "待定" : e.getResponsibility().getLabel()));
        }
        List<String> photos = new ArrayList<>();
        events.stream().map(IncidentEvent::getRectifyPhotoUrl)
                .filter(p -> p != null && !p.isBlank()).forEach(photos::add);
        vrs.stream().map(ViolationRecord::getRectifyPhotoUrl)
                .filter(p -> p != null && !p.isBlank()).forEach(photos::add);
        arc.setRectifyPhotos(photos);

        // 撤摊信息随档案保留，避免提前撤摊商户在下期评级中断档
        StallOwnership own = store.getOwnership(app.getStallNo());
        if (own != null && own.isWithdrawn()) {
            arc.setWithdrawnEarly(true);
            arc.setWithdrawnAt(own.getWithdrawnAt());
        }

        // 评级：以违规扣分为主，事故次数与高风险标记加权
        int demerit = points + events.size() * 2 + (app.isHighRisk() ? 4 : 0);
        String grade;
        String advice;
        if (demerit <= 5) {
            grade = "A";
            advice = "优质商户：下期优先选择核心位置，免缴用电保证金";
        } else if (demerit <= 15) {
            grade = "B";
            advice = "合格商户：下期正常分配摊位";
        } else if (demerit <= 30) {
            grade = "C";
            advice = "关注商户：下期限功率接入、靠配电箱末端位置，须缴用电保证金";
        } else {
            grade = "D";
            advice = "高风险商户：下期暂停报名或仅安排备用线路专人盯守";
        }
        arc.setGrade(grade);
        arc.setAllocationScore(Math.max(0, 100 - demerit));
        arc.setAdvice(advice);
        store.saveArchive(arc);

        app.setViolationPoints(points);
        app.setGrade(grade);
        app.setArchived(true);
        if (!Statuses.CLOSED.equals(app.getStatus())) {
            app.setStatus(Statuses.CLOSED);
        }
        store.saveApplication(app);
        return arc;
    }

    /**
     * 闭市：所有未归档摊位统一读表归档，包括夜间已提前撤摊（CLOSED）的摊位，
     * 确保撤摊商户在下期分配与评级中不断档。
     */
    public List<StallArchive> closeMarket(int defaultEndWh) {
        List<StallArchive> result = new ArrayList<>();
        for (PowerApplication app : store.allApplications()) {
            if (app.isArchived() || Statuses.REJECTED.equals(app.getStatus())) {
                continue;
            }
            // 已撤摊且完成读表结算的沿用其读数；其余按统一终值结算
            int end = app.getMeterEndWh() > 0 ? app.getMeterEndWh() : defaultEndWh;
            result.add(archive(app, end));
        }
        return result;
    }
}
