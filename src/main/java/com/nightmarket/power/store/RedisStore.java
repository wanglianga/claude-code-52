package com.nightmarket.power.store;

import com.nightmarket.power.model.*;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 以 Redis Hash 为核心的存储层：
 *  nm:user:*            用户（含密码）
 *  nm:box:*             配电箱
 *  nm:app:*             用电申请
 *  nm:conn:*            接线记录
 *  nm:temp:*            临时加电
 *  nm:event:*           用电事件
 *  nm:violation:*       违规扣分
 *  nm:fire:*            消防抽检
 *  nm:risk:*            风控措施
 *  nm:owner:stallNo     摊位归属
 *  nm:bill:vendor       电费账户
 *  nm:archive:*         闭市档案
 *  nm:seq               自增 ID
 *  nm:stall-date        营业日（夜市期次）
 */
@Component
public class RedisStore {

    private static final String P = "nm:";

    private final RedisTemplate<String, Object> redis;

    public RedisStore(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    public long nextId(String name) {
        Long v = redis.opsForValue().increment(P + "seq:" + name);
        return v == null ? 1 : v;
    }

    // ---------- 用户 ----------
    public void saveUser(User u) {
        redis.opsForValue().set(P + "user:" + u.getUsername(), u);
    }

    public User getUser(String username) {
        return (User) redis.opsForValue().get(P + "user:" + username);
    }

    public List<User> allUsers() {
        return scan(P + "user:*").stream()
                .map(k -> (User) redis.opsForValue().get(k))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(User::getUsername))
                .collect(Collectors.toList());
    }

    // ---------- 配电箱 ----------
    public void saveBox(ElectricBox b) {
        redis.opsForValue().set(P + "box:" + b.getId(), b);
    }

    public ElectricBox getBox(String id) {
        return (ElectricBox) redis.opsForValue().get(P + "box:" + id);
    }

    public List<ElectricBox> allBoxes() {
        return scan(P + "box:*").stream()
                .map(k -> (ElectricBox) redis.opsForValue().get(k))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ElectricBox::getZone).thenComparing(ElectricBox::getCode))
                .collect(Collectors.toList());
    }

    // ---------- 申请 ----------
    public void saveApplication(PowerApplication a) {
        redis.opsForValue().set(P + "app:" + a.getId(), a);
    }

    public PowerApplication getApplication(String id) {
        return (PowerApplication) redis.opsForValue().get(P + "app:" + id);
    }

    public List<PowerApplication> allApplications() {
        return scan(P + "app:*").stream()
                .map(k -> (PowerApplication) redis.opsForValue().get(k))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(PowerApplication::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public Optional<PowerApplication> findByStallNo(String stallNo) {
        return allApplications().stream()
                .filter(a -> a.getStallNo().equalsIgnoreCase(stallNo))
                .findFirst();
    }

    public List<PowerApplication> findByVendor(String username) {
        return allApplications().stream()
                .filter(a -> a.getVendorUsername().equalsIgnoreCase(username))
                .collect(Collectors.toList());
    }

    public List<PowerApplication> findByBox(String boxId) {
        return allApplications().stream()
                .filter(a -> boxId.equals(a.getBoxId()))
                .collect(Collectors.toList());
    }

    // ---------- 接线 ----------
    public void saveConnection(ConnectionRecord c) {
        redis.opsForValue().set(P + "conn:" + c.getId(), c);
    }

    public List<ConnectionRecord> allConnections() {
        return scan(P + "conn:*").stream()
                .map(k -> (ConnectionRecord) redis.opsForValue().get(k))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public Optional<ConnectionRecord> findConnectionByApp(String applicationId) {
        return allConnections().stream()
                .filter(c -> c.getApplicationId().equals(applicationId))
                .findFirst();
    }

    // ---------- 临时加电 ----------
    public void saveTemp(TempPowerRequest t) {
        redis.opsForValue().set(P + "temp:" + t.getId(), t);
    }

    public List<TempPowerRequest> allTemps() {
        return scan(P + "temp:*").stream()
                .map(k -> (TempPowerRequest) redis.opsForValue().get(k))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(TempPowerRequest::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<TempPowerRequest> tempsByApp(String applicationId) {
        return allTemps().stream()
                .filter(t -> t.getApplicationId().equals(applicationId))
                .collect(Collectors.toList());
    }

    // ---------- 事件 ----------
    public void saveEvent(IncidentEvent e) {
        redis.opsForValue().set(P + "event:" + e.getId(), e);
    }

    public IncidentEvent getEvent(String id) {
        return (IncidentEvent) redis.opsForValue().get(P + "event:" + id);
    }

    public List<IncidentEvent> allEvents() {
        return scan(P + "event:*").stream()
                .map(k -> (IncidentEvent) redis.opsForValue().get(k))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(IncidentEvent::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    // ---------- 违规 ----------
    public void saveViolation(ViolationRecord v) {
        redis.opsForValue().set(P + "violation:" + v.getId(), v);
    }

    public List<ViolationRecord> allViolations() {
        return scan(P + "violation:*").stream()
                .map(k -> (ViolationRecord) redis.opsForValue().get(k))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ViolationRecord::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<ViolationRecord> violationsByStall(String stallNo) {
        return allViolations().stream()
                .filter(v -> v.getStallNo().equalsIgnoreCase(stallNo))
                .collect(Collectors.toList());
    }

    // ---------- 消防抽检 ----------
    public void saveFire(FireInspection f) {
        redis.opsForValue().set(P + "fire:" + f.getId(), f);
    }

    public List<FireInspection> allFires() {
        return scan(P + "fire:*").stream()
                .map(k -> (FireInspection) redis.opsForValue().get(k))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(FireInspection::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    // ---------- 风控 ----------
    public void saveRisk(RiskControl r) {
        redis.opsForValue().set(P + "risk:" + r.getId(), r);
    }

    public List<RiskControl> allRisks() {
        return scan(P + "risk:*").stream()
                .map(k -> (RiskControl) redis.opsForValue().get(k))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(RiskControl::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public Optional<RiskControl> activeRiskOfStall(String stallNo) {
        return allRisks().stream()
                .filter(r -> r.getStallNo().equalsIgnoreCase(stallNo) && "ACTIVE".equals(r.getStatus()))
                .findFirst();
    }

    // ---------- 摊位归属 ----------
    public void saveOwnership(StallOwnership o) {
        redis.opsForValue().set(P + "owner:" + o.getStallNo(), o);
    }

    public StallOwnership getOwnership(String stallNo) {
        return (StallOwnership) redis.opsForValue().get(P + "owner:" + stallNo);
    }

    public List<StallOwnership> allOwnerships() {
        return scan(P + "owner:*").stream()
                .map(k -> (StallOwnership) redis.opsForValue().get(k))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ---------- 电费账户 ----------
    public void saveBilling(BillingAccount b) {
        redis.opsForValue().set(P + "bill:" + b.getVendorUsername(), b);
    }

    public BillingAccount getBilling(String vendorUsername) {
        return (BillingAccount) redis.opsForValue().get(P + "bill:" + vendorUsername);
    }

    public List<BillingAccount> allBillings() {
        return scan(P + "bill:*").stream()
                .map(k -> (BillingAccount) redis.opsForValue().get(k))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ---------- 档案 ----------
    public void saveArchive(StallArchive a) {
        redis.opsForValue().set(P + "archive:" + a.getId(), a);
    }

    public List<StallArchive> allArchives() {
        return scan(P + "archive:*").stream()
                .map(k -> (StallArchive) redis.opsForValue().get(k))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(StallArchive::getGrade))
                .collect(Collectors.toList());
    }

    // ---------- 营业日期 ----------
    public String marketDate() {
        Object v = redis.opsForValue().get(P + "stall-date");
        return v == null ? LocalDate.now().toString() : v.toString();
    }

    public void setMarketDate(String date) {
        redis.opsForValue().set(P + "stall-date", date);
    }

    /** 清空所有业务键（演示重置） */
    public void resetAll() {
        Set<String> keys = scan(P + "*");
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private Set<String> scan(String pattern) {
        Set<String> keys = new LinkedHashSet<>();
        var factory = redis.getConnectionFactory();
        if (factory == null) {
            return keys;
        }
        var conn = factory.getConnection();
        try (Cursor<byte[]> cursor = conn.scan(
                ScanOptions.scanOptions().match(pattern).count(500).build())) {
            while (cursor.hasNext()) {
                keys.add(new String(cursor.next()));
            }
        } finally {
            conn.close();
        }
        return keys;
    }
}
