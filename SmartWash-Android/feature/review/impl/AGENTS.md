# :feature:review:impl — 记忆复习实现（FSRS）

> api/impl 拆分：路由常量 `ReviewRoute` / `ReviewConstant` 与 `ReviewApi`/DTO 在
> `:feature:review:api`（单一事实来源）；本模块为实现（仓库/逻辑/页面/DI）。
> FSRS-6 调度全部在后端 `FsrsScheduler` 纯函数内完成，客户端只传 `(cardId, rating 1..4)`。

## 接口契约

见 worktree `docs/review-api.md`（本地文档，docs/ 不入库），后端实现
`SmartWash/src/main/java/com/smartwash/review/controller/ReviewWebController.java`，
前缀 `/web/auth/review/**`，需 ROLE_USER：

| 动作 | 端点 | 方法 |
|---|---|---|
| 创建资料 | `/materials` | POST（title 必填 ≤100，rawText 可空 ≤100k） |
| 资料分页 | `/materials?page=&size=` | GET |
| 建卡 | `/materials/{id}/cards` | POST（question ≤1000 / answer ≤2000） |
| 卡片分页 | `/cards?materialId=&page=&size=` | GET |
| 今日队列 | `/queue` | GET（到期卡在前 + 新卡队尾，每次最多带出 20 张新卡） |
| 复习评分 | `/answer` | POST（rating 1..4，durationMs 可空；服务端以自身时钟为准） |
| 复习统计 | `/stats` | GET（近 30 天每日数 + 评分占比；v1 页面未消费，预留） |

## 包结构

```
com.smartwash.feature.review/（契约在 :feature:review:api）
├── model/ReviewModels.kt       # ReviewRating(1=Again 2=Hard 3=Good 4=Easy) / ReviewCardState(1/2/3)
├── repository/                 # ReviewRepository 接口 + Impl（data 为 null 即失败）
├── di/ReviewModule.kt          # Retrofit create ReviewApi + @Binds Repository
├── logic/ReviewFormat.kt       # 纯逻辑：到期量级换算（DueText）+ 日期截取（配 JVM 锚点单测）
└── ui/
    ├── ReviewGraph.kt          # NavGraphBuilder.reviewGraph 聚合 + navigateToReviewStudy/notifyReviewDataChanged
    ├── ReviewUiCommon.kt       # ReviewTagChip/NewCardChip/DueTypeChip/CardStateChip/RatingButton/StudyProgressBar
    ├── queue/                  # 今日队列页（计数条锚点 + 队列列表）
    ├── study/                  # 复习页（翻面 AnimatedContent + 四档评分按钮）
    └── material/               # 资料页（建资料内联表单 + 展开卡片列表 + 建卡弹窗）
```

## 接线方式

- `settings.gradle` 声明 `:feature:review:api` / `:feature:review:impl`，`app/build.gradle` 同时依赖两者；
  壳层 MainActivity NavHost 一行 `reviewGraph(navController)` 聚合（模式同 toolboxGraph）。
- 队列页 ↔ 资料页/复习页的数据同步走 `ReviewRoute.CHANGED_KEY`（savedStateHandle 信号，模式同 ToolboxRoute.CHANGED_KEY）：
  资料页建资料/建卡成功、复习页每次评分成功后 `notifyReviewDataChanged()`，队列页消费后重拉。

## 页面行为要点

- **今日队列页**：`GET /queue` 一次拉全量（后端上限 50 到期 + 20 新卡）；按队列内 materialId
  翻页补齐「所属资料」标题（防御上限 `MATERIAL_TITLE_MAX_PAGES` 页）；条目点击 → 复习页并携带
  startCardId 定位起始。
- **复习页**：进入即 `GET /queue`（不复用队列页数据，避免传大列表过导航）；评分成功**本地移除**
  该卡并复位翻面自动下一张（不消费响应 VO）；`durationMs` 由卡面展示时刻折算；失败 toast 保留当前卡。
- **资料页**：资料/卡片均分页（PAGE_SIZE=20，加载更多按钮模式）；展开的资料与卡片列表状态集中在
  单一 UiState（切走即丢弃过期响应）。
- **契约缺口**：后端 v1 未提供改卡/删卡端点，页面不做假按钮——卡片仅支持新增与查看；
  后端补 PUT/DELETE 后在本模块扩展。

## 已知坑

- 时间字段全部为 ISO `yyyy-MM-dd'T'HH:mm:ss` 字符串（服务端 LocalDateTime 序列化），展示换算用
  `ReviewFormat`（本地时钟仅做展示量级判断，业务判定一律信服务端）。
- `stability/difficulty` 新卡为 null（Gson 映射 Double?）；`step` Review 态为 null。
- 评分按钮色取语义令牌：Again=danger / Hard=ongoing / Good=primary / Easy=water（Anki 红橙绿蓝惯例，
  主题切换自动跟随），不要另造色值。
