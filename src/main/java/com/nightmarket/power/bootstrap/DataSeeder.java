package com.nightmarket.power.bootstrap;

import com.nightmarket.power.model.*;
import com.nightmarket.power.service.*;
import com.nightmarket.power.store.RedisStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时播种演示数据：六类角色账号、配电箱（含备用线路）、
 * 各业态摊位申请（含一个明火高风险待复核）、接线记录、预付电费账户。
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    public static final String DEMO_PASSWORD = "night123";

    private final RedisStore store;
    private final PasswordEncoder encoder;
    private final ApplicationService applicationService;
    private final ConnectionService connectionService;
    private final BillingService billing;
    private final boolean resetOnStart;

    public DataSeeder(RedisStore store, PasswordEncoder encoder,
                      ApplicationService applicationService, ConnectionService connectionService,
                      BillingService billing,
                      @Value("${demo.reset-on-start:false}") boolean resetOnStart) {
        this.store = store;
        this.encoder = encoder;
        this.applicationService = applicationService;
        this.connectionService = connectionService;
        this.billing = billing;
        this.resetOnStart = resetOnStart;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!resetOnStart) {
            return;
        }
        store.resetAll();
        log.info("[演示数据] 开始播种 …");

        // ---- 账号（六个角色） ----
        createUser("vendor01", "王建军", Role.VENDOR, "13800000001");
        createUser("vendor02", "李桂兰", Role.VENDOR, "13800000002");
        createUser("vendor03", "赵德柱", Role.VENDOR, "13800000003");
        createUser("vendor04", "陈美玲", Role.VENDOR, "13800000004");
        createUser("vendor05", "周大壮", Role.VENDOR, "13800000005");
        createUser("vendor06", "吴游乐", Role.VENDOR, "13800000006");
        createUser("electrician01", "马强", Role.ELECTRICIAN, "13900000001");
        createUser("admin01", "林芳", Role.ADMIN, "13700000001");
        createUser("security01", "赵虎", Role.SECURITY, "13600000001");
        createUser("cashier01", "孙倩", Role.CASHIER, "13500000001");
        createUser("fire01", "郑刚", Role.FIREFIGHTER, "13511900119");

        // ---- 配电箱（含备用线路箱） ----
        saveBox("BXA-01", "A区·小吃街", 6000, false);
        saveBox("BXA-02", "A区·小吃街", 3000, false);
        saveBox("BXB-01", "B区·烧烤广场", 6000, false);
        saveBox("BXC-01", "C区·游艺区", 4000, false);
        saveBox("BX-BACKUP", "备用线路", 5000, true);

        // ---- 摊位申请（提交即自动生成审批） ----
        // A01：明火烧烤，高风险 → 系统初审转管理员复核
        PowerApplication a01 = applicationService.submit("A01", "老王烧烤", "vendor01", "A区·小吃街",
                StallType.FOOD,
                List.of(new Device(DeviceKind.STOVE, "电烤炉", 2000, 1),
                        new Device(DeviceKind.LIGHTING, "摊位照明", 100, 2)),
                true, false, true, "18:00-24:00");

        // A02：饮品冷柜 → 自动批准
        PowerApplication a02 = applicationService.submit("A02", "李姐冰粉", "vendor02", "A区·小吃街",
                StallType.DRINK,
                List.of(new Device(DeviceKind.FREEZER, "卧式冷柜", 800, 1),
                        new Device(DeviceKind.LIGHTBOX, "发光招牌", 200, 1)),
                false, true, true, "17:30-23:30");

        // A03：小功率零售 → 自动批准
        applicationService.submit("A03", "赵哥百货", "vendor03", "A区·小吃街",
                StallType.RETAIL,
                List.of(new Device(DeviceKind.LIGHTBOX, "LED灯牌", 150, 1)),
                false, false, true, "18:00-23:00");

        // B01/B02：同一摊主两个摊位（共用预付账户）
        applicationService.submit("B01", "陈姐烤冷面", "vendor04", "B区·烧烤广场",
                StallType.SNACK,
                List.of(new Device(DeviceKind.STOVE, "烤冷面机", 1500, 1)),
                true, false, true, "18:00-23:30");
        applicationService.submit("B02", "陈姐酸梅汤", "vendor04", "B区·烧烤广场",
                StallType.DRINK,
                List.of(new Device(DeviceKind.FREEZER, "冷柜", 600, 1)),
                false, true, false, "18:00-23:30");

        // B03：可用于临时转让演示
        applicationService.submit("B03", "大壮铁板", "vendor05", "B区·烧烤广场",
                StallType.SNACK,
                List.of(new Device(DeviceKind.STOVE, "铁板炉", 1200, 1)),
                true, false, true, "18:00-24:00");

        // C01：游艺音响
        applicationService.submit("C01", "游乐K歌摊", "vendor06", "C区·游艺区",
                StallType.GAME,
                List.of(new Device(DeviceKind.SPEAKER, "专业音响", 1000, 2),
                        new Device(DeviceKind.LIGHTBOX, "霓虹灯牌", 300, 1)),
                false, false, true, "19:00-24:00");

        // ---- 电工为 A02 现场接线送电（示范完整接线记录） ----
        connectionService.connect(a02.getId(), "electrician01", "SOCKET-A02-03",
                "漏保试跳正常·30mA", 0,
                "/photos/cable-A02-20260912.jpg", "/signatures/vendor02-A02.png",
                true, "线缆架空敷设，远离水源");

        // ---- 预付电费 ----
        billing.recharge("vendor01", 100);
        billing.recharge("vendor02", 200);
        billing.recharge("vendor03", 80);
        billing.recharge("vendor04", 120);   // 一个账户供两个摊位
        billing.recharge("vendor05", 60);
        billing.recharge("vendor06", 5);     // 低余额，用于预付不足预警演示

        log.info("[演示数据] 播种完成：A01 高风险待管理员复核；A02 已接线送电；统一密码 {}", DEMO_PASSWORD);
    }

    private void createUser(String username, String name, Role role, String phone) {
        User u = new User(username, encoder.encode(DEMO_PASSWORD), name, role, phone);
        store.saveUser(u);
    }

    private void saveBox(String code, String zone, int capacityW, boolean backup) {
        String id = "BOX-" + code;
        ElectricBox b = new ElectricBox(id, zone, code, capacityW, backup);
        store.saveBox(b);
    }
}
