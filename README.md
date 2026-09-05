# SmartWash 校园智能洗衣平台

校园智能洗衣一站式服务平台，覆盖 **用户端（Android / 鸿蒙 NEXT）**、**管理后台（Web）** 和 **后端服务**，四端共享同一套后端 API 与 MySQL 数据库。

---

## 目录

- [项目架构](#项目架构)
- [技术栈](#技术栈)
- [功能模块](#功能模块)
- [数据库设计](#数据库设计)
- [后端 API 契约](#后端-api-契约)
- [环境配置](#环境配置)
- [快速开始](#快速开始)
- [默认账号](#默认账号)
- [开发规范](#开发规范)
- [已知风险与待办](#已知风险与待办)

---

## 项目架构

```
SmartWash/
├── SmartWash/              # Spring Boot 3.4 后端服务
├── SmartWash-Android/      # Android 用户端（Kotlin / Jetpack Compose）
├── SmartWash_Harmony/      # 鸿蒙 NEXT 用户端（ArkTS / ArkUI）
├── SmartWashWeb/           # Vue 3 Web 管理后台
├── smart_wash.sql          # MySQL 数据库初始化脚本（基准结构）
└── docs/                   # 项目文档
    ├── code-review-2026-08-28.md        # 四端深度评审报告（P0/P1/P2 问题清单）
    └── 占卜模块后端与数据库架构设计.md
```

### 后端分层架构

```
com.smartwash/
├── controller/
│   ├── LoginController.java  # 登录/注册/验证码（顶层）
│   ├── web/                  # 移动端 API（Android / 鸿蒙消费）+ AppDownloadController、AppVersionController
│   └── background/           # 后台管理 API（Web 消费）
├── service/                  # 业务逻辑层（接口 + impl/）
├── mapper/                   # MyBatis-Plus 数据访问层（自定义 SQL 在 resources/mapper/）
├── entity/                   # 数据库实体
├── from/                     # 请求表单对象（DTO）
├── vo/                       # 视图响应对象（VO）
├── config/                   # Spring 配置（Security / CORS / Redis / MyBatis / JWT / Minio）
├── filter/                   # JWT 认证过滤器
├── common/                   # 公共类（统一响应、分页等）
├── utils/                    # 工具类
├── task/                     # 定时任务（OrderTimeoutManager）
├── exception/                # 全局异常定义
└── divination/               # 「观象台」AI 占卜子系统
    ├── controller/           # 占卜 Web/Admin 接口
    ├── core/                 # 六十四卦 / 六壬 / 奇门 / 梅花算法内核
    ├── llm/                  # LLM 网关与 SSE 流式解读
    ├── prompt/               # Prompt 模板管理
    ├── entity/ / mapper/ / service/ / vo/ / from/
    └── task/                 # 审计与用量聚合任务
```

各端消费关系：

| 接口前缀 | 消费端 | 权限 |
|---------|--------|------|
| `/auth/**` | 全部 | 公开 |
| `/web/auth/**` | Android / 鸿蒙 | ROLE_USER |
| `/web/**` | Android / 鸿蒙 | 公开 |
| `/admin/**` | Web 管理后台 | ROLE_ADMIN |

---

## 技术栈

### 后端

| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 17 | — |
| Spring Boot | 3.4.0 | Web / Security / Validation / Data Redis / Cache |
| MyBatis-Plus | 3.5.7 | ORM，`map-underscore-to-camel-case` 自动映射 |
| MySQL Connector | 9.7.0 | 驱动 |
| Redis (Spring Data) | — | 缓存 / Session |
| JWT (jjwt) | 0.12.6 | 无状态认证 |
| Hutool | 5.8.35 | 工具库 |
| FastJSON | 2.0.56 | JSON 序列化 |
| Minio | — | 文件存储（图片上传） |
| Lombok | 1.18.30 | 样板代码 |

### Android 用户端

| 组件 | 版本 | 说明 |
|------|------|------|
| Kotlin | — | — |
| compileSdk / targetSdk | 35 | minSdk 30 |
| Jetpack Compose BOM | 2025.03.00 | 声明式 UI |
| Material 3 | — | Material Design 3 |
| Hilt | 2.51 | 依赖注入 |
| Retrofit | 2.11.0 | 网络请求 |
| Room | 2.6.1 | 本地数据库（卦历缓存） |
| DataStore Preferences | 1.1.3 | 键值存储（Token 等） |
| Navigation Compose | 2.8.9 | 导航 |
| Paging 3 | — | 分页 |
| Coil | 2.7.0 | 图片加载 |
| ZXing Core | — | 二维码生成 |

### 鸿蒙 NEXT 用户端

| 组件 | 说明 |
|------|------|
| ArkTS / ArkUI | 声明式 UI |
| Stage 模型 | 应用模型 |
| API 版本 | 5.0.5(17)（compatible / target 一致） |
| @ohos/axios | 网络请求 |

### Web 管理后台

| 组件 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5.13 | 渐进式框架 |
| Vite | 6.1.0 | 构建工具 |
| Element Plus | 2.9.6 | UI 组件库 |
| Pinia | 3.0.1 | 状态管理 |
| Vue Router | 4.5.0 | 路由 |
| Axios | 1.8.2 | HTTP 客户端 |
| ECharts | 6.1.0 | 数据可视化（仪表盘） |
| dayjs | 1.11.13 | 日期处理 |
| Vitest | 4.1.5 | 单元测试 |
| ESLint + Prettier | — | 代码规范 |

---

## 功能模块

### 用户端（Android / 鸿蒙）

| 模块 | 说明 |
|------|------|
| 注册 / 登录 | 手机号 + 验证码，JWT 认证 |
| 首页 | 轮播、服务入口、推荐套餐 |
| 洗衣套餐 | 浏览 / 选购洗衣项目，提交订单 |
| 订单 | 列表、详情、状态追踪（待寄件 → 已收取 → 清洗中 → 已烘干 → 配送中 → 待取件 → 已完成） |
| 订单评价 | 评分 + 文字评价 |
| 取件 | 储物柜取件码 |
| 优惠券 | 领取 / 查看可用优惠券 |
| 充值 | 钱包余额充值（支持多种充值类型） |
| 支付 | 余额支付 / 优惠券核销 |
| 个人中心 | 用户信息修改、设置 |
| 观象台（占卜） | 六十四卦起卦、AI 流式解读、卦历本地缓存（Android） |

### 管理后台（Web）

| 模块 | 说明 |
|------|------|
| 仪表盘 | 订单 / 用户 / 收入概览（ECharts） |
| 用户管理 | CRUD、状态管理 |
| 学校管理 | 学校 CRUD、地图选点（高德） |
| 储物柜管理 | 储物柜 CRUD、关联学校 |
| 洗衣项目管理 | 套餐 CRUD、图片上传（Minio） |
| 订单管理 | 订单列表、状态流转、详情 |
| 支付记录 | 支付流水查询 |
| 充值记录 | 充值流水查询 |
| 优惠券模板 | 优惠券 CRUD |
| 用户优惠券 | 已发放优惠券管理 |
| 角色权限 | 超级管理员 / 学校管理员 / 厂房管理员 |
| 后台用户 | 管理员账号管理 |
| 观象台管理 | 模型目录、Prompt 版本、RAG 语料、审计复审、拦截日志、用量看板、平台设置 |

---

## 数据库设计

### 核心表

| 表名 | 说明 |
|------|------|
| `users` | 用户表（手机号、密码、余额、学校等） |
| `admin_users` | 后台管理员（root / admin / lisi） |
| `roles` | 角色表（root 超级管理员 / schools_admin 学校管理员） |
| `schools` | 学校信息（含经纬度） |
| `lockers` | 储物柜（格口、关联学校） |
| `laundry_items` | 洗衣套餐（名称、价格、图片） |
| `orders` | 订单（状态、金额、关联用户/套餐/储物柜） |
| `order_reviews` | 订单评价 |
| `payments` | 支付记录（含幂等键） |
| `recharge_records` | 充值记录（含幂等键） |
| `coupon` | 优惠券模板 |
| `user_coupon` | 用户领取优惠券记录 |

### 占卜子系统表（divination，共 11 张）

| 表名 | 说明 |
|------|------|
| `div_record` | 起卦记录 |
| `div_interpretation` | AI 解读记录 |
| `div_feedback` | 用户反馈 |
| `div_prompt_version` | Prompt 版本管理 |
| `div_rag_document` | RAG 语料文档 |
| `div_rag_chunk` | RAG 语料分块 |
| `div_blocked_question` | 拦截问题黑名单 |
| `div_usage_daily` | 每日用量统计 |
| `div_model_config` | LLM 模型配置 |
| `div_platform_setting` | 平台设置 |
| `div_user_api_config` | 用户 API 配置 |

### 数据库结构管理

> 数据库结构统一由根目录 `smart_wash.sql` 管理。结构变更请直接修改该文件。

---

## 后端 API 契约

### 统一响应信封

```json
{ "code": 200, "message": "success", "data": { ... } }
```

- 业务成功 `code = 200`，HTTP 状态码只反映传输层，业务语义看 `code`。
- 401 语义三端必须一致：清空本地 token → 清内存登录态 → replace（非 push）到登录页，并发 401 去重只跳一次。

### 认证

JWT Bearer token，`sub` 带前缀 `admin-{用户名}` 或 `user-{手机号}`。

### 接口清单（节选）

**移动端（`/web/**`）：**

| 接口 | 说明 |
|------|------|
| `POST /auth/user/login` | 用户登录（手机号） |
| `POST /auth/user/register` | 用户注册 |
| `POST /auth/adminUsers/login` | 管理员登录（用户名） |
| `GET /web/laundryItems/all` | 洗衣套餐列表 |
| `POST /web/auth/orders/reservation` | 创建订单（预约洗衣） |
| `GET /web/auth/orders` | 我的订单列表 |
| `POST /web/auth/payments/payment` | 发起支付 |
| `POST /web/auth/recharge/userRecharge` | 钱包充值 |
| `GET /web/auth/coupon/allCoupon` | 可领优惠券 |
| `POST /web/auth/userCoupon/receiveCoupon/{id}` | 领取优惠券 |
| `GET /web/app/version` | App 版本检查 |
| `GET /web/app/download` | APK 预签名下载链接 |

**管理端（`/admin/**`）：**

| 接口 | 说明 |
|------|------|
| `POST /auth/adminUsers/login` | 管理员登录 |
| `GET /admin/users` | 用户列表 |
| `GET /admin/orders` | 订单列表 |
| `POST /admin/laundryItems` | 新增洗衣项目 |
| `GET /admin/dashboard` | 仪表盘数据 |
| 观象台管理接口 | 模型/Prompt/RAG/审计/黑名单/用量/设置（见 `controller/background/` 与 `divination/controller/`） |

---

## 环境配置

### 后端（环境变量注入）

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DB_HOST` / `DB_PORT` | 127.0.0.1 / 3306 | MySQL 地址 |
| `DB_USERNAME` / `DB_PASSWORD` | root / admin123 | MySQL 凭证 |
| `REDIS_HOST` / `REDIS_PORT` | 127.0.0.1 / 6379 | Redis 地址 |
| `JWT_SECRET` | 占位默认值 | **生产必须覆盖** |
| `CORS_ORIGINS` | http://localhost:*,http://192.168.* | 跨域白名单 |
| `MINIO_ENDPOINT` | http://192.168.1.61:9000 | 文件存储 |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | minioadmin | **生产必须覆盖** |

提供 `application-dev.yaml`（开发）与 `application-prod.yaml`（生产）两套 profile。

### Web 管理后台

| 文件 | 说明 |
|------|------|
| `.env.development` | `VITE_BASE_URL=/api`（Vite 代理转发至 `127.0.0.1:8080`） |
| `.env.production` | 生产 API 地址由构建时环境变量 `SMART_WASH_BASE_URL` 注入（不再写死 IP） |
| `.env.development` / `.env.production` | `VITE_AMAP_KEY`（当前为空，需配置）、`VITE_AMAP_SECURITY_CODE`（高德地图，旧值已泄露待轮换） |

### Android

`local.properties` 配置 SDK 路径；`applicationId = "com.smartwash"`；BASE_URL 通过 Gradle 属性 `baseUrl` 注入（生产 `-PbaseUrl=https://your-domain.com/`）。

### 鸿蒙

`oh-package.json5` 管理依赖；`build-profile.json5` 配置 SDK 版本。

---

## 快速开始

### 1. 数据库初始化

```bash
mysql -u root -p < smart_wash.sql
```

> 后续结构变更请直接修改 `smart_wash.sql`。

### 2. 启动后端

```bash
cd SmartWash
# 配置环境变量或修改 application-dev.yaml
export DB_HOST=127.0.0.1 DB_USERNAME=root DB_PASSWORD=admin123
export JWT_SECRET=your-strong-secret
mvn spring-boot:run
```

后端默认端口 `8080`。

### 3. 启动 Web 管理后台

```bash
cd SmartWashWeb
npm install
npm run dev
```

访问 `http://localhost:5000`，API 请求由 Vite 代理转发至后端。

### 4. 启动 Android 客户端

用 Android Studio 打开 `SmartWash-Android` 目录，同步 Gradle 后运行。需确保后端已启动且 `BASE_URL` 指向正确（默认指向演示服务器，生产通过 `-PbaseUrl=` 注入）。

### 5. 启动鸿蒙客户端

用 DevEco Studio 打开 `SmartWash_Harmony` 目录，同步后运行到鸿蒙设备或模拟器。

---

## 默认账号

### 管理后台

| 用户名 | 角色 | 说明 |
|--------|------|------|
| `root` | 超级管理员 (root) | 全部权限 |
| `admin` | 学校管理员 (schools_admin) | 指定学校管理权限 |
| `lisi` | 学校管理员 (schools_admin) | 指定学校管理权限 |

> 初始密码为 BCrypt 哈希，见 `smart_wash.sql` 种子数据；生产部署前务必修改。

### 用户端

需通过手机号注册。

---

## 开发规范

### 提交规范

- 一个 commit 对应一个完整功能点
- commit message 中文，格式：`<type>(<scope>): <描述>`
- scope 取值：`Backend` / `Android` / `Harmony` / `Web` / `SQL`
- 跨端改动拆分提交或在描述中列明

### 接口联动

改动任何接口必须四端联动检查：

| 改动内容 | 需要同步的位置 |
|---------|--------------|
| 新增/修改用户端接口 | 后端 `controller/web/` + Android `network/api/` + 鸿蒙 `network/api/` |
| 新增/修改管理端接口 | 后端 `controller/background/` + Web `src/api/` |
| 修改响应结构/错误码 | 三端各自的响应包装解析 |
| 修改枚举/状态码 | 三端各自的枚举映射 |

### 环境红线

- **生产禁明文 HTTP**：网络层地址必须按环境注入（dev 内网 / prod HTTPS），禁止硬编码
- **密钥不入库**：JWT_SECRET、DB 密码、高德 key / securityJsCode、支付密钥一律走环境变量或本地未跟踪配置文件
- **品牌图标不替换**：微信/支付宝等品牌色和图标不得替换为通用组件

---

## 已知风险与待办

详细问题清单与优先级路线图见 **[docs/code-review-2026-08-28.md](docs/code-review-2026-08-28.md)**。

### P0（必须修复）

- 后端资金链路（支付/充值/优惠券）存在并发竞态与幂等缺失
- 鸿蒙端 BASE_URL 硬编码演示服务器明文 HTTP（待环境化）
- 高德安全密钥已入库 / 入 env（需轮换）

### P1

- Android `usesCleartextTraffic=true` demo 全局放行（发版前须移除）
- 部分接口缺少参数校验
- 鸿蒙 token 明文存 preferences（待迁移 HUKS/Asset Store）

> 做任何涉及订单、支付、充值、优惠券的改动前，先读评审报告第一章的 P0 项。

---

## 子项目文档

各端详细开发指南请查阅各自的 `CLAUDE.md`：

- [SmartWash/CLAUDE.md](SmartWash/CLAUDE.md) — 后端开发规范
- [SmartWash-Android/CLAUDE.md](SmartWash-Android/CLAUDE.md) — Android 开发规范
- [SmartWash_Harmony/CLAUDE.md](SmartWash_Harmony/CLAUDE.md) — 鸿蒙开发规范
- [SmartWashWeb/CLAUDE.md](SmartWashWeb/CLAUDE.md) — Web 开发规范
