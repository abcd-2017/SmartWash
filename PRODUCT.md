# Product

<!-- impeccable:product-schema 1 -->

## Platform

adaptive

## Users

- **学生（主用户）**：衡阳及周边中小学的住校学生，中学生自带手机，独立完成注册登录、预约洗衣、支付、凭取件码取件。核心任务：把换洗衣物送洗并按时取回。
- **家长（部分参与）**：低龄段学生由家长代为下单与支付；家长视角围绕孩子所属学校的订单展开。
- **管理端运营人员**：`root`（超级管理员）、`schools_admin`（学校管理员，管理本校储物柜/洗衣项目/订单/优惠券）；`plant`（厂房管理员）角色已预留但**未启用**，权限未细化。

## Product Purpose

SmartWash 是校园智能洗衣一站式服务平台：学生在 App（Android / 鸿蒙 NEXT）选择学校与洗衣项目、预约下单、支付，将衣物投入宿舍楼下的智能储物柜，中央厂房集中清洗烘干后配送回柜，学生凭取件码/二维码取件。管理后台（Web）供运营方管理学校、储物柜、洗衣项目、订单、资金流水与优惠券。

本项目为个人/团队学习项目：无真实商业运营压力，价值标准是产品全链路（预约→支付→清洗→取件）走通、四端联动质量与工程规范完整度。成功指标未正式定义（开放事项）。

## Positioning

以**储物柜（locker）为载体的校园取送洗衣**：投衣入柜、取件码取衣、厂房集中清洗配送，替代上门取送与到店自送两种传统模式；数据按学校维度组织，支持多校运营。同一后端同时支撑 App 端洗衣主航道与平台内实验性增值模块（观象台 AI 占卜、AI 助手、工作台短链工具箱、H5 游戏中心）。

## Operating Context

- **使用场景**：校园宿舍楼/教学楼下的储物柜取还；清洗在中央厂房完成（订单流转：待寄件→已收取→清洗中→已烘干→配送中→待取件→已完成）。
- **管理场景**：运营方通过 Web 后台完成学校（地图选点）、储物柜、洗衣项目、订单 CSV 导出、支付/充值流水、优惠券、账号角色的日常管理。
- **部署**：`deploy/deploy.sh`（/opt/smartwash）——GitHub Actions 按版本 tag 构建，APK 经 MinIO 分发并 SHA256 校验热更新，后端/Web 走阿里云 ACR 镜像滚动更新；演示服务器 `http://8.148.70.81:9000`。
- **开发**：后端 `mvn spring-boot:run`（MySQL 8 + Redis，8080）；Web `npm run dev`（5000，代理 /api→8080）；Android Studio / `./gradlew`；鸿蒙 DevEco / hvigorw。

## Capabilities and Constraints

**核心能力**：手机号注册登录（校园卡绑定）、学校选择、洗衣项目（种子 5 档：29.9–199.9）、预约下单（30 分钟未支付自动取消）、支付（钱包余额/支付宝/微信，当前为桩实现 `StubPaymentGatewayServiceImpl`）、充值与流水、优惠券（模板发放/领取/门槛核销）、订单评价、取件码与二维码、APK 热更新。

**平台实验模块**（四端联动，允许更大设计自由度）：站内信消息中心、积分签到与月榜、订阅管理、情绪气象站、记忆复习（FSRS）、工具箱（短链 + 设备投递）、游戏中心（H5 游戏 + 存档/排行榜/关卡热更新）、解压实验舱（纯本地玩具）、工作台（后端配置化入口 + 深链分发）、观象台（AI 占卜：四术排盘 + LLM SSE 解读 + BYOK）。

**硬性契约与约束**（来自根 AGENTS.md 与各端 CLAUDE.md，改动不得绕过）：

- 统一响应信封 `{code, message, data}`，业务成功 code=200；401 语义四端一致（清 token → replace 登录页，只跳一次），并已接入双 token 单飞续签（access 2h + refresh 7d）。
- 订单状态机：0 待支付 / 1 待寄件 / 2 已收取 / 3 清洗中 / 4 已烘干 / 5 配送中 / 6 待取件 / 7 已完成 / -1 已退款 / -2 已取消。
- 资金操作（支付/充值/核销）后端以条件更新保证幂等；`payments`/`recharge_records` 禁止新增删除接口；金额展示用 tabular numbers。
- 生产禁明文 HTTP；密钥一律环境变量注入。
- 管理端写操作必须加 `@Audited`（HMAC 审计链）；公开高频接口按需加 `@RateLimit`（Redis Lua 分布式限流）。
- Android 端 UI 必须走「清氧」设计系统：禁引入动画库（Lottie/Rive/Shimmer/Vico）、禁新增 `Icons.*`、动效必须带 reduced motion 分支（有静态门禁脚本强制）。
- Web 端：权限以后端鉴权为准、禁 v-html、CRUD 页禁止复制第 12 份（需抽通用组件）。

**未决/开放事项**：`docs/adr/` 仍未创建（GLOSSARY 中 ADR-0001~0007 为引用占位，游戏数据平台/站内信/积分/订阅/情绪/复习/审计/限流等新领域待补 ADR）；产品中文名与 slogan 缺失；成功标准未正式定义；`plant` 角色职责边界未定义；鸿蒙端无障碍标注空白；Android 缺 `order-detail/{orderId}` 深链（鸿蒙已注册）。

## Brand Commitments

- **名称**：SmartWash（applicationId `com.smartwash`；无官方中文名）。
- **主色**：`#2D9B6A` 绿——红线规定不改（暗色子主题「玄墨」除外）。
- **设计系统**：Android 端「清氧」设计系统（暗色「清氧·夜」，模块子主题「玄墨」），规范见 `SmartWash-Android/.claude/ui-design-spec.md`；签名图形「滚筒圆」DrumIcon（圆筒旋转动画）；支付图标必须使用微信/支付宝品牌 svg，禁止替换为通用组件。
- **文案**：中文、简洁、克制；六色浅底图标容器、每屏一个视觉锚点的 UI 风格。
- **Logo 资源**：Android `app/src/main/res/drawable/logo.png` + mipmap；鸿蒙 `entry/src/main/resources/base/media/logo.png`；Web `public/favicon.ico`。

## Evidence on Hand

- `smart_wash.sql`：9 所衡阳市中小学种子数据、5 档洗衣项目价格、完整状态枚举与角色种子；含平台模块 30+ 张表。
- `README.md`：功能模块表、API 契约、默认账号（root/admin/lisi，生产必改）。
- `GLOSSARY.md`：四端共享术语表（含游戏数据平台、站内信、积分、订阅、情绪、复习、审计、限流等）。
- `docs/code-review-2026-08-28.md`：四端深度评审问题清单与优先级（做订单/支付/充值/优惠券改动前必读）。
- `SmartWash-Android/.claude/ui-design-spec.md`：清氧设计系统完整规范（本仓库唯一成文的设计规范文档）。
- **不存在**：真实用户数据、运营案例、用户评价、媒体报道——任何设计与文案不得虚构此类内容。

## Product Principles

1. **四端联动优先**：同一接口最多四处消费，任何功能与界面改动先核对 Android/鸿蒙/Web/后端的联动成本，再决定怎么做。
2. **学生自助为主、家长可代劳**：核心链路按学生独立操作设计；家长代管不改变主流程结构。
3. **资金链路可信**：支付、充值、优惠券的界面状态必须与后端幂等/状态机语义严格一致，宁可少展示，不可误显示。
4. **工程完整度即产品价值**：以全链路走通、四端规范一致、部署链路完整作为学习项目的成功标准。
5. **洗衣是主航道，增值模块是实验田**：观象台/游戏/工具箱允许更大的设计自由度，但不得挤占洗衣主流程的清晰度与一致性。

## Accessibility & Inclusion

- Android 端已有强制基线：reduced motion 检测（`ANIMATOR_DURATION_SCALE=0`）并全树降级、关键图标 `contentDescription`、金额/数量/取件码 tabular numbers。
- 待补：TalkBack/读屏专项、字体缩放适配、鸿蒙端无障碍标注——目前无记录。
