# 夜市摊位临时用电申请与跳闸追责服务

基于 **Java 17 + Spring Boot 3 + Spring Security + Redis** 的 0-1 工程，覆盖夜市临时用电「报名审批 → 现场接线 → 临时加电 → 跳闸追责 → 风控处置 → 闭市评级」全生命周期，并把**摊主、电工、市场管理员、安保、收费人员、消防部门**串到同一条用电责任链上。

---

## 原始需求

> 开发夜市摊位临时用电申请与跳闸追责服务，可采用 Java、Spring Boot 和 Redis。摊主报名夜市时提交摊位类型、用电设备、额定功率、营业时段、是否使用明火、冷柜和照明需求，服务根据摊位位置、配电箱容量、线路负载、历史违规和同区域高峰生成用电审批。电工现场接线后，记录插座编号、漏保状态、电表初值、线缆照片和摊主签字。营业中，摊主可能临时增加烤炉、冰柜、灯牌或音响，服务要重新计算同箱负载并判断是否允许加电。若发生跳闸、线路发热、私拉电线、雨水浸泡或顾客投诉异味，服务把摊主、电工、市场管理员、安保和收费人员串到同一用电事件中。恢复供电前，需要确认故障点、受影响摊位、食品损耗、顾客赔付和是否暂停某摊营业。夜市结束后，用电量、违规扣分、临时加电费、跳闸责任和整改照片进入档案，用于下期摊位分配和安全评级。服务还要处理商户夜间撤摊、临时摊位转让、同一摊主多摊共用设备、电费预付不足和消防部门抽检，确保用电责任不会因为市场现场混乱而断开。对高风险摊位，服务需要支持管理员临时限制设备开启、要求电工复检或把摊位迁到备用线路，并把市场广播和收费调整同步记录。

---

## 一、快速启动（宿主 Docker Compose 一键部署）

前置：宿主已安装 Docker 与 Docker Compose v2。

```bash
# 1. 准备环境变量（评审环境通常已注入 CC_PUBLISH_PORT / COMPOSE_PROJECT_NAME）
cp .env.example .env

# 2. 一键启动（app + redis；首次构建约 1-3 分钟，含 Maven 依赖下载）
docker compose up -d

# 3. 查看健康状态（start_period 60s，启动后应为 healthy）
docker compose ps
curl -s http://127.0.0.1:${CC_PUBLISH_PORT:-3052}/actuator/health
# {"status":"UP"}

# 4. 浏览器打开（宿主侧）
#    http://localhost:3052            （CC_PUBLISH_PORT 默认 3052）
#    或在评审容器内： http://host.docker.internal:3052

# 5. 验证结束释放资源
docker compose down            # 如需连演示数据一起删除：docker compose down -v
```

- 应用容器内监听 `8080`，仅 app 端口发布到宿主：`"${CC_PUBLISH_PORT}:8080"`。
- Redis **不发布宿主端口**，仅在 compose 内部网络通过服务名 `redis:6379` 互访；数据用 `redis-data` 卷持久化（appendonly）。
- 多阶段 Dockerfile：`maven:3.9-eclipse-temurin-17` 构建 → `eclipse-temurin:17-jre` 运行，**非 root 用户 appuser**，内置 **HEALTHCHECK**（`/actuator/health`）。
- 每次 `docker compose up -d` 且 `DEMO_RESET_ON_START=true`（compose 默认）时会重置并重新播种演示数据，保证评审环境可复现。

---

## 二、测试账号（密码统一 `night123`，BCrypt 存储）

| 用户名 | 角色 | 姓名 | 权限/可做的事 |
|---|---|---|---|
| `admin01` | 市场管理员 ADMIN | 林芳 | 高风险申请复核、跳闸责任认定、恢复/暂停营业、风控（限设备/复检/迁备用线）、市场广播、收费调整、摊位转让/共用/撤摊、闭市归档评级 |
| `electrician01` | 电工 ELECTRICIAN | 马强 | 现场接线（插座/漏保/电表初值/线缆照片/摊主签字）、确认故障点与整改照片、高风险复检、恢复送电 |
| `security01` | 安保 SECURITY | 赵虎 | 首报跳闸/发热/私拉/水浸/异味、紧急断电、到场疏散留痕 |
| `cashier01` | 收费人员 CASHIER | 孙倩 | 电费预存充值、临时加电费/赔付台账、欠费巡检断电 |
| `fire01` | 消防部门 FIREFIGHTER | 郑刚 | 夜间用电消防抽检（合格/限期整改/立即停业），结果进违规档案 |
| `vendor01` | 摊主 VENDOR | 王建军 | 明火烧烤摊 **A01（高风险，待管理员复核）** |
| `vendor02` | 摊主 VENDOR | 李桂兰 | **A02 已接线送电**（冷柜+灯牌），适合试临时加电 |
| `vendor03` | 摊主 VENDOR | 赵德柱 | A03 百货零售 |
| `vendor04` | 摊主 VENDOR | 陈美玲 | **B01、B02 同一摊主两个摊位，共用一个预付账户** |
| `vendor05` | 摊主 VENDOR | 周大壮 | B03 铁板（可做临时转让演示） |
| `vendor06` | 摊主 VENDOR | 吴游乐 | C01 游艺音响（初始余额仅 5 元，可演示预付不足） |

登录后按角色自动进入对应 Web 控制台（Thymeleaf 暗色界面，全部操作可点）；同一套能力也提供 REST API。

---

## 三、核心业务规则（实现说明）

### 1. 报名与自动审批
摊主提交：摊位类型、用电设备（种类/额定功率/数量）、营业时段、是否明火、冷柜/照明需求。
系统按以下因子自动核定并选择配电箱：

- **核定负载** = Σ(设备功率 × 数量 × 设备负载系数) × 摊位类型同时系数 + 照明基线（有照明需求但未申报时补 300W）。
  - 设备系数：烤炉/电热 1.00、冷柜 1.00、照明 0.90、灯牌 0.80、音响 0.50。
  - 业态同时系数：餐饮 1.00、小吃 0.90、饮品 0.85、游艺 0.80、零售 0.60。
- **选箱**：同区域非备用、非故障配电箱中，选「预测负载率最低、已分配摊位最少」的箱；预测负载 = 已批/在营摊位核定负载 + 本摊负载，同区域在营 ≥3 个进入**晚高峰**再 ×1.10。
- 无箱可容纳 → 系统直接驳回；**风险分 ≥ 40 判高风险**，转管理员复核。
- 风险分构成：业态基础分 + 明火 +20 + 冷柜 +6 + 申报功率档（>3kW +10 / >1.5kW +5）+ 历史违规（累计分/2，封顶30）+ 同区域高峰 +5 + 箱体限制 +10 + 审批后箱负载率≥90% +8。

### 2. 电工现场接线
记录插座编号、漏保（漏电保护器）状态、电表初值、线缆照片 URL、摊主签字；**插座编号、漏保状态、线缆照片、摊主签字任一缺失（含空签字 URL）接口直接返回 400，不写入任何记录，申请保持「已批准·待接线」**；高风险摊位若被要求电工复检且未通过，同样不能接线。补齐全部现场凭证后才能送电，申请进入「营业中」。

### 3. 营业中临时加电审批（容量 + 邻近摊位 + 电工到场三重检查，两阶段生效）
摊主临时增加烤炉/冰柜/灯牌/音响时提交审批，服务依次检查：

1. **同配电箱剩余容量**：实时重算「同箱在营负载 + 本次负载（经设备/业态系数折算）」，超过安全容量（额定 × 0.8）即拒绝；
2. **邻近摊位负载（冷柜保护）**：列出同箱每个在营摊位的实时负载，若同箱有冷柜且加电后负载率 ≥ 90%，为避免晚高峰顶掉别人冷柜造成食材损耗，直接拒绝；
3. **电工到场时间**：值班电工 ETA = 10 分钟 + 每个待接线任务 8 分钟，超过摊主可接受等待（默认 15 分钟，申请可指定）即拒绝；同箱有处置中的故障事件时标记无法到场。

**审批拒绝**：返回 400/业务拒绝，并给出可执行的**替代建议**——降功率（反推可申报额定功率上限）/换低功率设备、错峰（22:30 邻近冷柜收摊后）、迁移到有余量的备用线路箱（自动计算剩余容量）、充值后重试等；拒绝不产生任何费用。

**审批通过（PENDING_APPROVAL）**：
- 加电费（2 元/100W）立即从预付账户扣除并写入当晚摊位记录；生成电工「到场接线任务」通知（含 ETA、设备、箱号）；
- 向**同配电箱其他每个在营摊主**推送加电预告，通知中明确**加电摊位（如 A01）、设备功率、风险起讫时间（电工预计到场时刻～当晚营业结束）、加电后负载率**以及"跳闸/异味时风险来源为该摊，请立即联系管理员"，避免别人冷柜跳闸却不知道风险来源；同时通知申请人（含安全提示：禁止私拉插线板、远离明火积水、跳闸立即断设备）与收费人员。
- **电工到场复核**：确认现场容量仍满足后，填写独立插座编号与接线照片才真正送电（ALLOWED）；收费、接线照片、安全提示进入当晚摊位记录；若现场已超载则拒绝接线并**原路退还加电费**。送电瞬间再向同箱摊主推送「加电已生效·风险即刻开始」通知。

### 4. 跳闸/险情事件与五方追责
事件类型：跳闸、线路发热、私拉电线、雨水浸泡、顾客投诉异味（另含消防抽检隐患）。

- 任何人（安保/管理员）首报后：系统自动把**摊主、接线电工、值班管理员、值班安保、值班收费**五方串入同一事件，同箱在营摊位**立即全部断电**，跳闸/水浸时配电箱置 FAULT。
- 事件时间线（Timeline）记录每一方的动作。
- 安保可登记到场处置；电工确认**故障点、整改情况、整改照片**。
- **恢复供电硬前置**：必须已确认故障点且整改完成，否则 400 拒绝复电。
- 管理员责任认定：责任方（摊主/电工/市场方/不可抗力）、扣分、**食品损耗、顾客赔付、暂停某摊营业**；认定后违规入档（责任在摊主才对摊主扣分），摊主责任的赔付自动从其预付账户扣款并由收费人员在时间线登记。
- 复电时配电箱复位，同箱摊位恢复在营，但**被责令停业、被风控限制设备的摊位不自动复电**。

### 5. 高风险风控（管理员）
一条风控措施同时记录：临时限制开启的设备清单、要求电工复检、迁移到备用线路箱（只允许 `backupLine=true` 的箱）、**市场广播原文**、**收费调整金额（+返还/−加收，同步入电费台账）**。措施生效期间在营摊位暂停供电；电工复检通过且无未结事件后可复电；管理员可解除措施。

### 6. 责任链不断点的现场变动
- **夜间撤摊**：读电表终值 → 已接电摊位按读数结算电费（1.5 元/度），未接电摊位保留终值读数留痕但不计电费 → 断电关闭，记录撤摊时间。**撤摊即视为当期责任已结清，闭市时该摊仍会生成档案（带「夜间撤摊」标记与撤摊时间），不会在下期评级中断档**；闭市可重复执行（幂等），已归档摊位不会重复出档。
- **临时摊位转让**：管理员见证后，申请责任人与归属档案切到受让摊主，后续事件/赔付/评级均找得到当前责任人。
- **多摊共用设备**：两摊双向登记共用设备描述，事故影响可连带追溯。
- **同一摊主多摊共用一个预付电费账户**（vendor04 的 B01/B02）。
- **预付不足**：账户低余额预警；余额为负且经收费巡检 → 该摊主名下在营摊位断电。
- **消防抽检**：合格/限期整改/立即停业；不合格直接停业，扣分与整改照片进档案。

### 7. 闭市结算、档案与安全评级
闭市对当期**所有未归档摊位（含已提前撤摊的 CLOSED 摊位，被驳回的除外）**统一读表结算，每摊生成唯一一份档案：用电量、电费、违规明细与扣分、临时加电次数/费用、跳闸事件与责任认定、整改照片，并标记是否夜间提前撤摊及撤摊时间。
评级（扣分为主，事故次数与高风险加权）：

- **A（≤5 分）**：下期优先核心位置、免用电保证金；
- **B（≤15）**：正常分配；
- **C（≤30）**：限功率、末端位置、缴保证金；
- **D（>30）**：暂停报名或仅备用线路专人盯守。

---

## 四、推荐浏览器演示路径（约 5 分钟）

1. `vendor01` 登录（需先让管理员批准、电工接线 A01）→ ② 给 A01 申请 1200W 烤炉：因同箱 A02 冷柜、加后 92% 触发**邻近冷柜保护拒绝**，看到降功率/错峰/迁备用线建议；改申请 500W 冰柜 → **审批通过待电工接线**。`vendor02` 登录可看到指向 A01 的加电预告（含风险时段）；`electrician01` 在「临时加电接线任务」中到场拍照送电后，`vendor02` 再收到「已生效」通知。
2. `admin01` 登录 → ① 复核批准高风险 **A01**。
3. `electrician01` 登录 → ① 为 A01 接线送电（默认表单已填插座/漏保/照片/签字）。
4. `security01` 登录 → 上报「A01 跳闸」→ 看到 A01/A02 同时断电；登记到场处置。
5. 直接点「恢复送电」→ 被系统拒绝（未确认故障点）；电工填写故障点+整改照片。
6. `admin01` 在事件卡片上：责任选「摊主」、扣 6 分、食品损耗 80、顾客赔付 120、暂停摊位填 `A01`、取消勾选立即复电 → 提交；再由电工复电 → **A02 复电、A01 停业**；收费台可看到向摊主收 120 元赔付的台账。
7. `admin01` → ④ 对 A02 下风控（限制冷柜+复检+迁 BX-BACKUP 备用线+广播+收费调整）；电工复检；管理员解除后 A02 复电。
8. `fire01` 抽检 C01 选「不合格·立即停业」→ C01 立即停业。
9. `cashier01` 给某摊主充值；多次临时加电把余额用尽时系统直接拒绝；制造负余额后点「巡检欠费并断电」。
10. ⑤ 试 A03 夜间撤摊（读表结算）、B03 临时转让给 vendor06、B01/B02 共用设备登记。
11. ⑥「夜市闭市」→ ⑦ 查看各摊 A/B/C/D 评级、扣分、跳闸责任、整改照片与下期分配建议。

---

## 五、REST API 摘要（除 /login 与健康检查外均需会话登录 + 角色）

| 方法/路径 | 角色 | 说明 |
|---|---|---|
| `GET /api/overview` | 任意登录 | 总览统计 + 配电箱实时负载 |
| `GET /api/applications` `/api/applications/{id}` | 任意登录 | 申请列表/详情（含接线、加电、违规、风控） |
| `GET /api/boxes` `/events` `/violations` `/fires` `/risks` `/temps` `/billing` `/ownership` `/archives` `/users` | 任意登录 | 各类台账查询 |
| `POST /api/vendor/applications` | 摊主/管理员 | 报名提交，即时生成审批 |
| `POST /api/vendor/temp-power` | 摊主/管理员 | 临时加电审批（容量/邻近冷柜/电工ETA；通过=待接线，拒绝附替代建议） |
| `POST /api/electrician/temp-requests/{id}/hookup` | 电工 | 到场复核+插座+接线照片，加电正式生效（超载则拒接并退费） |
| `GET /api/notifications/mine` `POST /api/notifications/{id}/read` | 任意登录 | 加电预告/批准/生效、接线任务、收费通知 |
| `POST /api/vendor/billing/recharge` | 摊主/管理员 | 给自己（管理员可代他人）充值 |
| `POST /api/electrician/connections` | 电工 | 现场接线送电 |
| `POST /api/electrician/events/{id}/fault` | 电工 | 故障点/整改/照片确认 |
| `POST /api/electrician/events/{id}/recover` | 电工 | 恢复送电（有前置校验） |
| `POST /api/electrician/risks/{id}/recheck` | 电工 | 高风险复检结论 |
| `POST /api/security/incidents` | 安保/管理员 | 首报险情（五方串联+同箱断电） |
| `POST /api/security/events/{id}/dispatch` | 安保/管理员 | 到场处置留痕 |
| `POST /api/admin/applications/{id}/approve|reject` | 管理员 | 高风险复核 |
| `POST /api/admin/incidents` | 管理员 | 首报险情 |
| `POST /api/admin/incidents/{id}/resolve` | 管理员 | 责任认定+损耗/赔付/暂停营业核查 |
| `POST /api/admin/incidents/{id}/recover|close` | 管理员 | 复电/关闭归档 |
| `POST /api/admin/risks` `/risks/{id}/lift` | 管理员 | 风控措施（限设备/复检/迁线/广播/收费调整）/解除 |
| `POST /api/admin/stalls/withdraw|transfer|share-device` | 管理员 | 撤摊/转让/共用设备 |
| `POST /api/admin/billing/enforce-arrears` | 管理员 | 欠费巡检断电 |
| `POST /api/admin/archives/{stallNo}` `/api/admin/close-market` | 管理员 | 单摊归档/闭市统一归档评级 |
| `POST /api/cashier/recharge` `/api/cashier/enforce-arrears` | 收费/管理员 | 充值/欠费断电 |
| `POST /api/fire/inspections` | 消防/管理员 | 消防抽检 |
| `GET /actuator/health` | 公开 | 健康检查 |

接口鉴权：Spring Security 表单登录（Session Cookie），方法级 `@PreAuthorize` 控制角色；越权返回 403。

### curl 示例

```bash
BASE=http://host.docker.internal:3052
# 登录（保存会话）
curl -c /tmp/admin.txt -d 'username=admin01&password=night123' "$BASE/login"
# 总览
curl -s -b /tmp/admin.txt "$BASE/api/overview"
# 安保上报跳闸
curl -s -c /tmp/sec.txt -d 'username=security01&password=night123' "$BASE/login"
curl -s -b /tmp/sec.txt -X POST "$BASE/api/security/incidents" \
  -H 'Content-Type: application/json' \
  -d '{"type":"TRIP","stallNo":"A01","description":"高峰跳闸","severity":4}'
# 临时加电
curl -s -c /tmp/v.txt -d 'username=vendor02&password=night123' "$BASE/login"
curl -s -b /tmp/v.txt -X POST "$BASE/api/vendor/temp-power" \
  -H 'Content-Type: application/json' \
  -d '{"applicationId":"A2","kind":"STOVE","deviceName":"加台烤炉","ratedPowerW":500,"quantity":1}'
```

---

## 六、工程结构与技术要点

```
src/main/java/com/nightmarket/power/
├── NightMarketPowerApplication.java
├── config/            RedisConfig(JSON 序列化+JavaTime)、SecurityConfig(BCrypt/表单登录/方法鉴权)
├── model/             User/Role/StallType/DeviceKind/IncidentType/Responsibility/InspectionResult
│                      ElectricBox/PowerApplication/ConnectionRecord/TempPowerRequest
│                      IncidentEvent(五方+时间线)/ViolationRecord/BillingAccount
│                      FireInspection/RiskControl/StallOwnership/StallArchive/Statuses
├── store/RedisStore   Redis Hash 存储 + SCAN + 自增序列（nm: 前缀）
├── service/           DirectoryService / PowerCalcService(负载与风险引擎)
│                      ApplicationService / ConnectionService / TempPowerService
│                      IncidentService(追责核心) / BillingService / OwnershipService
│                      FireInspectionService / RiskControlService / ArchiveService
│                      BizException + DataSeeder(演示播种)
└── web/               6 个角色 REST 控制器 + 查询控制器 + Thymeleaf 页面控制器 + DTO/异常处理
src/main/resources/
├── application.yml
├── templates/         login + 6 个角色控制台（原生 JS fetch，无外部前端依赖）
└── static/css,js
```

- 数据全部存 Redis（键设计见 `RedisStore` 顶部注释），JSON 序列化保留多态类型信息。
- 无外部图片服务：线缆/签字/整改照片以 URL/路径字符串留痕（演示用占位路径）。

## 七、验证方式说明（本工程已实际验证）

本工程在隔离容器内通过**宿主 Docker Compose** 完成验证：

- `docker compose up -d` 后 app 与 redis 均 `healthy`，`/actuator/health` 返回 `{"status":"UP"}`；
- 六个角色账号均可登录并进入各自控制台（HTTP 200），越权返回 403、未登录 302；
- 走通：自动审批/高风险复核 → 接线 → 临时加电（超载驳回、余额不足驳回、正常允许计费）→ 跳闸五方串联与同箱断电 → 无故障点/未整改禁止复电的前置拦截 → 责任认定/扣分/赔付/暂停营业/差异化复电 → 风控（限设备/复检/迁备用线/广播/收费调整）→ 消防抽检停业 → 欠费断电 → 撤摊结算/转让/共用设备 → 闭市归档与 A/B/C/D 评级。
- 验证结束执行 `docker compose down` 释放资源。
