# SmartWash 校园智能洗衣平台

校园智能洗衣一站式服务平台：学生在 App（Android / 鸿蒙 NEXT）选择学校与洗衣项目、预约下单、支付，把衣物投入宿舍楼下的智能储物柜，中央厂房集中清洗烘干后配送回柜，学生凭取件码 / 二维码取件；运营方通过 Web 管理后台管理学校、储物柜、洗衣项目、订单、资金与优惠券。

四端（后端 + Android + 鸿蒙 + Web）共享同一套后端 API 与 MySQL 数据库。

> 本项目为个人 / 团队学习项目，价值标准是产品全链路（预约 → 支付 → 清洗 → 取件）走通、四端联动质量与工程规范完整度。

---

## 目录

- [架构总览](#架构总览)
- [技术栈](#技术栈)
- [功能概览](#功能概览)
- [快速开始](#快速开始)
- [默认账号](#默认账号)
- [开发规范与联动](#开发规范与联动)
- [已知风险](#已知风险)
- [相关文档](#相关文档)

---

## 架构总览

```
SmartWash/
├── SmartWash/              # Spring Boot 3.4 后端（Java 17 / Maven）
├── SmartWash-Android/      # Android 用户端（Kotlin / Jetpack Compose / Hilt）
├── SmartWash_Harmony/      # 鸿蒙 NEXT 用户端（ArkTS / ArkUI）
├── SmartWashWeb/           # Vue 3 Web 管理后台
├── smart_wash.sql          # MySQL 建表 + 种子数据（结构变更以本文件为准）
├── .claude/docs/           # agent 工作规则（提交规范、共享规则）
└── docs/                   # 本地工作文档（gitignore，不入库）
```

后端分层：

```
com.smartwash/
├── controller/        # web/（移动端 API）+ background/（管理端 API）+ 顶层登录 / 注册
├── service/           # 业务逻辑（接口 + impl/）
├── mapper/            # MyBatis-Plus 数据访问（自定义 SQL 在 resources/mapper/）
├── entity/ from/ vo/  # 实体 / 请求表单 / 视图响应
├── config/ filter/ common/ utils/ task/ exception/
└── divination/ game/ notice/ points/ review/ subscription/ toolbox/ workbench/ dining/  # 平台模块
```

接口前缀与权限（后端 SecurityConfig 决定，三端不要绕过）：

| 接口前缀 | 消费端 | 权限 |
|---------|--------|------|
| `/auth/**` | 全部 | 公开（登录、注册、验证码、刷新） |
| `/web/auth/**` | Android / 鸿蒙 | ROLE_USER |
| `/web/**` | Android / 鸿蒙 | 公开 |
| `/admin/**` | Web 管理后台 | ROLE_ADMIN |

---

## 技术栈

| 端 | 技术 |
|----|------|
| 后端 | Java 17 · Spring Boot 3.4（Web / Security / Validation / Data Redis / Cache）· MyBatis-Plus 3.5.7 · MySQL · Redis · JWT（jjwt 0.12.6，双 token）· MinIO · Hutool · FastJSON · Lombok |
| Android | Kotlin · Jetpack Compose（Material 3）· Hilt · Retrofit · Room · DataStore · Navigation Compose · Paging 3 · Coil · ZXing |
| 鸿蒙 NEXT | ArkTS / ArkUI · Stage 模型 · @ohos/axios |
| Web | Vue 3 · Vite 6 · Element Plus · Pinia · Vue Router · Axios · ECharts · dayjs · Vitest |

---

## 功能概览

### 洗衣主航道

**用户端（Android / 鸿蒙）**：手机号 + 验证码注册登录、首页服务入口、洗衣套餐浏览与预约下单、订单状态追踪（待寄件 → 已收取 → 清洗中 → 已烘干 → 配送中 → 待取件 → 已完成）、余额支付 / 充值 / 优惠券核销、取件码与二维码、订单评价、个人中心、消息中心。

**管理后台（Web）**：仪表盘（订单 / 用户 / 收入趋势）、用户与学校管理、储物柜管理、洗衣项目与套餐、订单管理与状态流转、支付 / 充值流水、优惠券模板与发放、角色权限与后台账号、操作审计、站内信管理。

### 平台实验模块

在主航道之外，四端联动落地了一系列增值 / 实验模块：积分签到与月榜、订阅管理、记忆复习（FSRS 调度）、工具箱（短链 + 设备投递）、游戏中心（H5 游戏 + 存档 / 排行榜 / 关卡）、实验舱（纯本地解压玩具）、工作台（后端配置化入口 + 深链分发）、观象台（AI 占卜：四术排盘 + LLM 流式解读 + BYOK）、吃啥（公共菜谱库 + 拼单点餐 + 全局扫一扫加入）。

> 各模块术语与四端对应关系见 [GLOSSARY.md](GLOSSARY.md)；产品定位与原则见 [PRODUCT.md](PRODUCT.md)。

---

## 快速开始

### 1. 初始化数据库

```bash
mysql -u root -p < smart_wash.sql
```

> 后续结构变更请直接修改 `smart_wash.sql`。

### 2. 启动后端

```bash
cd SmartWash
export DB_HOST=127.0.0.1 DB_USERNAME=root DB_PASSWORD=admin123
export JWT_SECRET=your-strong-secret
mvn spring-boot:run
```

后端默认端口 `8080`。生产环境变量清单见 `application-prod.yaml` 与各端 `CLAUDE.md`。

### 3. 启动 Web 管理后台

```bash
cd SmartWashWeb
npm install
npm run dev        # http://localhost:5000，/api 代理至 8080
```

### 4. 启动 Android / 鸿蒙客户端

- **Android**：Android Studio 打开 `SmartWash-Android`，Gradle 同步后运行；`BASE_URL` 通过 Gradle 属性 `baseUrl` 注入（生产 `-PbaseUrl=https://your-domain.com/`）。
- **鸿蒙**：DevEco Studio 打开 `SmartWash_Harmony`，同步后运行到设备 / 模拟器。

---

## 默认账号

| 用户名 | 角色 | 说明 |
|--------|------|------|
| `root` | 超级管理员 | 全部权限 |
| `admin` / `lisi` | 学校管理员 | 指定学校管理权限 |

> 初始密码为 BCrypt 哈希，见 `smart_wash.sql` 种子数据；生产部署前务必修改。用户端需手机号注册。

---

## 开发规范与联动

- **提交规范**：一个 commit 对应一个完整功能点，中文 Conventional Commits，见 [commit-conventions.md](.claude/docs/commit-conventions.md)。
- **四端联动**：改动任何接口必须同步后端 + Android + 鸿蒙 + Web，检查表见根目录 [AGENTS.md](AGENTS.md)。
- **环境红线**：生产禁明文 HTTP、密钥一律环境变量注入、管理端写操作必须 `@Audited`、公开高频接口按需 `@RateLimit`。
- **工作文档归属**：工作产物（调研 / 评审 / 方案）放 `docs/`（不入库），规则类文档放 `.claude/docs/`（入库）。

---

## 已知风险

四端深度评审（2026-08-28）的问题清单与优先级路线图见 **[docs/code-review-2026-08-28.md](docs/code-review-2026-08-28.md)**。

**P0**：后端资金链路（支付 / 充值 / 优惠券）存在并发竞态与幂等缺失；鸿蒙端 `BASE_URL` 仍为硬编码明文 HTTP；高德安全密钥待轮换。

> 做任何涉及订单、支付、充值、优惠券的改动前，先读评审报告第一章的 P0 项。

---

## 相关文档

- [AGENTS.md](AGENTS.md) / [CLAUDE.md](CLAUDE.md) — 仓库总纲与四端联动硬规则
- [PRODUCT.md](PRODUCT.md) — 产品定位、原则与品牌约定
- [GLOSSARY.md](GLOSSARY.md) — 四端共享术语表
- [docs/agents/](docs/agents/) — agent 工作流（issue tracker / triage / domain docs）
- 各端规范：[SmartWash/CLAUDE.md](SmartWash/CLAUDE.md) · [SmartWash-Android/CLAUDE.md](SmartWash-Android/CLAUDE.md) · [SmartWash_Harmony/CLAUDE.md](SmartWash_Harmony/CLAUDE.md) · [SmartWashWeb/CLAUDE.md](SmartWashWeb/CLAUDE.md)
