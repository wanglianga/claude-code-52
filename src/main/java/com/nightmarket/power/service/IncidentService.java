package com.nightmarket.power.service;

import com.nightmarket.power.model.*;
import com.nightmarket.power.store.RedisStore;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用电事件（跳闸/线路发热/私拉电线/雨水浸泡/投诉异味）处置与追责。
 * 一个事件把摊主、电工、管理员、安保、收费人员串在同一时间线上：
 *  报告 → 到场处置（安保）→ 故障点确认（电工）→ 影响与损失登记 →
 *  责任认定（管理员）→ 整改照片 → 恢复送电 → 违规入档 / 收费扣款。
 */
@Service
public class IncidentService {

    private final RedisStore store;
    private final PowerCalcService calc;
    private final BillingService billing;
    private final DirectoryService directory;

    public IncidentService(RedisStore store, PowerCalcService calc, BillingService billing,
                           DirectoryService directory) {
        this.store = store;
        this.calc = calc;
        this.billing = billing;
        this.directory = directory;
    }

    /** 任何人发现险情均可报告，系统自动把五方人员串入事件 */
    public IncidentEvent report(IncidentType type, String stallNo, String reporter, Role reporterRole,
                                String description, int severity) {
        PowerApplication app = store.findByStallNo(stallNo)
                .orElseThrow(() -> new BizException("摊位 " + stallNo + " 无用电档案"));
        ElectricBox box = store.getBox(app.getBoxId());

        IncidentEvent e = new IncidentEvent("E" + store.nextId("event"));
        e.setType(type);
        e.setBoxId(app.getBoxId());
        e.setPrimaryStallNo(stallNo);
        e.setReporter(reporter);
        e.setReporterRole(reporterRole);
        e.setDescription(description);
        e.setSeverity(severity);

        // ---- 把五方责任人串到同一事件 ----
        e.setVendorUsername(app.getVendorUsername());
        store.findConnectionByApp(app.getId()).ifPresent(c -> e.setElectricianUsername(c.getElectricianUsername()));
        if (e.getElectricianUsername() == null) {
            User duty = directory.duty(Role.ELECTRICIAN);
            e.setElectricianUsername(duty == null ? null : duty.getUsername());
        }
        User admin = directory.duty(Role.ADMIN);
        User sec = directory.duty(Role.SECURITY);
        User cashier = directory.duty(Role.CASHIER);
        e.setAdminUsername(admin == null ? null : admin.getUsername());
        e.setSecurityUsername(sec == null ? null : sec.getUsername());
        e.setCashierUsername(cashier == null ? null : cashier.getUsername());

        // ---- 同箱在营摊位全部列为受影响摊位并立即断电 ----
        List<String> affected = new ArrayList<>();
        for (PowerApplication a : store.findByBox(app.getBoxId())) {
            if (Statuses.OPERATING.equals(a.getStatus())) {
                affected.add(a.getStallNo());
                a.setStatus(Statuses.POWER_CUT);
                store.saveApplication(a);
            }
        }
        if (type == IncidentType.TRIP || type == IncidentType.WATERLOG) {
            if (box != null) {
                box.setStatus("FAULT");
                store.saveBox(box);
            }
        }
        e.setAffectedStalls(affected);
        e.setResponsibility(type.getSuggested());
        e.setDeductPoints(type.getDefaultPoints());
        e.addTimeline(reporter, reporterRole,
                "报告[" + type.getLabel() + "]：" + description + "；同箱受影响摊位 " + affected
                        + "，已紧急断电，通知五方到场");
        store.saveEvent(e);
        return e;
    }

    /** 安保到场维持秩序/疏散 */
    public IncidentEvent securityDispatch(String eventId, String security, String action) {
        IncidentEvent e = must(eventId);
        e.setSecurityUsername(security);
        e.addTimeline(security, Role.SECURITY, "安保到场：" + action);
        store.saveEvent(e);
        return e;
    }

    /** 电工确认故障点、受影响范围与抢修结论 */
    public IncidentEvent confirmFault(String eventId, String electrician, String faultPoint,
                                      boolean rectified, String rectifyPhoto) {
        IncidentEvent e = must(eventId);
        e.setElectricianUsername(electrician);
        e.setFaultPoint(faultPoint);
        e.setFaultConfirmedBy(electrician);
        e.setRectified(rectified);
        e.setRectifyPhotoUrl(rectifyPhoto);
        e.addTimeline(electrician, Role.ELECTRICIAN,
                "确认故障点：" + faultPoint + "；整改" + (rectified ? "已完成" : "未完成")
                        + (rectifyPhoto == null || rectifyPhoto.isBlank() ? "" : "；整改照片 " + rectifyPhoto));
        store.saveEvent(e);
        return e;
    }

    /**
     * 管理员责任认定 + 恢复供电前核查：
     * 故障点、受影响摊位、食品损耗、顾客赔付、暂停某摊营业。
     */
    public IncidentEvent resolve(String eventId, String admin, Responsibility resp, Integer points,
                                 Integer foodLoss, Integer compensation, String suspendStall,
                                 String liabilityRemark, boolean recoverNow) {
        IncidentEvent e = must(eventId);
        e.setAdminUsername(admin);
        e.setResponsibility(resp);
        if (points != null) {
            e.setDeductPoints(points);
        }
        e.setFoodLossWan(foodLoss);
        e.setCustomerCompensationWan(compensation);
        e.setStallSuspended(suspendStall);
        e.setLiabilityRemark(liabilityRemark);
        e.addTimeline(admin, Role.ADMIN,
                "责任认定=" + resp.getLabel() + "，扣 " + e.getDeductPoints()
                        + " 分；食品损耗 " + nz(foodLoss) + " 元，顾客赔付 " + nz(compensation)
                        + " 元" + (suspendStall == null || suspendStall.isBlank() ? "" : "；暂停摊位 " + suspendStall + " 营业")
                        + "；" + liabilityRemark);
        store.saveEvent(e);

        // 暂停营业
        if (suspendStall != null && !suspendStall.isBlank()) {
            store.findByStallNo(suspendStall).ifPresent(a -> {
                a.setStatus(Statuses.SUSPENDED);
                store.saveApplication(a);
            });
        }

        // 违规入档（责任在摊主时扣分记到摊主；其他责任记录留痕，不扣摊主分）
        ViolationRecord v = new ViolationRecord("V" + store.nextId("violation"));
        v.setStallNo(e.getPrimaryStallNo());
        v.setIncidentId(e.getId());
        v.setType(e.getType());
        v.setResponsibility(resp);
        v.setDetail(liabilityRemark);
        v.setRecorder(admin);
        v.setRecorderRole(Role.ADMIN);
        v.setRectifyPhotoUrl(e.getRectifyPhotoUrl());
        if (resp == Responsibility.VENDOR) {
            v.setVendorUsername(e.getVendorUsername());
            v.setPoints(e.getDeductPoints());
        } else {
            v.setPoints(0);
        }
        store.saveViolation(v);

        // 收费：摊主责任 → 摊主承担赔付；市场方责任 → 市场承担（演示中不扣摊主）
        if (compensation != null && compensation > 0 && resp == Responsibility.VENDOR) {
            billing.chargeCompensation(e.getVendorUsername(), compensation,
                    "事件 " + e.getId() + " 顾客赔付/食品损耗分摊");
            if (e.getCashierUsername() != null) {
                e.addTimeline(e.getCashierUsername(), Role.CASHIER,
                        "收费登记：向摊主收取顾客赔付/食品损耗 " + compensation + " 元");
            }
        } else if ((compensation != null && compensation > 0) || (foodLoss != null && foodLoss > 0)) {
            if (e.getCashierUsername() != null) {
                e.addTimeline(e.getCashierUsername(), Role.CASHIER,
                        "收费登记：责任方为[" + resp.getLabel() + "]，食品损耗 " + nz(foodLoss)
                                + " 元 / 顾客赔付 " + nz(compensation) + " 元由市场台账记录，不向摊主收取");
            }
        }
        store.saveEvent(e);

        if (recoverNow) {
            doRecover(e, admin);
        }
        return e;
    }

    /** 电工/管理员恢复送电：需已确认故障点并完成整改 */
    public IncidentEvent recover(String eventId, String actor, Role role) {
        IncidentEvent e = must(eventId);
        return doRecover(e, actor, role);
    }

    private IncidentEvent doRecover(IncidentEvent e, String actor) {
        return doRecover(e, actor, Role.ADMIN);
    }

    private IncidentEvent doRecover(IncidentEvent e, String actor, Role role) {
        if (Statuses.EVENT_CLOSED.equals(e.getStatus()) || Statuses.EVENT_RECOVERED.equals(e.getStatus())) {
            throw new BizException("事件 " + e.getId() + " 已恢复/关闭");
        }
        if (e.getFaultPoint() == null || e.getFaultPoint().isBlank()) {
            throw new BizException("恢复供电前必须先由电工确认故障点");
        }
        if (!e.isRectified()) {
            throw new BizException("故障整改未完成，不得恢复供电");
        }
        ElectricBox box = store.getBox(e.getBoxId());
        if (box != null && "FAULT".equals(box.getStatus())) {
            box.setStatus("NORMAL");
            store.saveBox(box);
        }
        List<String> restored = new ArrayList<>();
        for (String stall : e.getAffectedStalls()) {
            PowerApplication a = store.findByStallNo(stall).orElse(null);
            if (a == null) {
                continue;
            }
            // 被责令停业或仍处于风控限制的摊位不自动复电
            if (Statuses.SUSPENDED.equals(a.getStatus()) || Statuses.CLOSED.equals(a.getStatus())) {
                continue;
            }
            if (store.activeRiskOfStall(stall).map(RiskControl::isRestrictDevices).orElse(false)) {
                continue;
            }
            a.setStatus(Statuses.OPERATING);
            store.saveApplication(a);
            restored.add(stall);
        }
        e.setStatus(Statuses.EVENT_RECOVERED);
        e.setRecoveredBy(actor);
        e.setRecoveredAt(LocalDateTime.now());
        e.addTimeline(actor, role, "恢复供电；配电箱复位，复电台账：" + restored
                + "（被停业/限制摊位除外）");
        store.saveEvent(e);
        return e;
    }

    /** 事件关闭（赔付、整改全部闭环） */
    public IncidentEvent close(String eventId, String admin) {
        IncidentEvent e = must(eventId);
        e.setStatus(Statuses.EVENT_CLOSED);
        e.addTimeline(admin, Role.ADMIN, "事件关闭，材料归入摊位夜间档案");
        store.saveEvent(e);
        return e;
    }

    public IncidentEvent must(String id) {
        IncidentEvent e = store.getEvent(id);
        if (e == null) {
            throw new BizException("用电事件不存在: " + id);
        }
        return e;
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
