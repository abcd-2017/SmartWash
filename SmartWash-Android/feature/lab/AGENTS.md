# AGENTS.md — feature:lab（解压实验舱）

## 1. 模块身份

- **Gradle 坐标**：`:feature:lab`
- **包名**：`com.smartwash.feature.lab`
- **职责**：三个纯本地解压玩具（泡泡纸 / 呼吸引导 / 指尖鱼群）+ 实验舱首页入口卡。**模块内零网络请求、零后端接口、零数据库**——不依赖 `:common:network`，不建 Api/Repository 网络层；玩具状态全部会话内即弃（泡泡纸计数不持久化）。
- **入口**（完全复用「纯深链 + 后端配置卡片」架构，同 `:feature:game`）：
  - 深链 `smartwash://lab`（本模块 `di/LabDeepLinkModule` 经 `@IntoSet` 贡献 host `lab` → `LabRoute.Home`）
  - 后端 `workbench_entry` 配置的工作台卡片（`smart_wash.sql` 种子登记，`icon key = science`、`color = purple`、`target = smartwash://lab`），`WorkbenchPage` 零改动
  - 无底栏入口

## 2. 包结构

```
com.smartwash.feature.lab
├── LabRoute.kt                     # 路由常量密封类（Home/BubbleWrap/Breath/Fish）+ LabToy 入口枚举
├── di/
│   └── LabDeepLinkModule.kt        # @IntoSet DeepLinkContributor：host "lab" → LabRoute.Home
├── util/                           # （玩具音效等纯本地工具）
│   └── PopSoundPlayer.kt           # AudioTrack 程序化合成 pop 短音（零音频资产）
└── ui/
    ├── LabGraph.kt                 # NavGraphBuilder.labGraph：4 个 composable 注册
    ├── home/                       # 实验舱首页（三张玩具入口卡）
    ├── bubble/                     # 泡泡纸（Canvas 泡泡网格 + 戳破动画 + pop 音）
    ├── breath/                     # 呼吸引导（相位状态机 + 圆环动效 + tnum 倒计时）
    └── fish/                       # 指尖鱼群（BoidsSimulator 内核 + withFrameNanos tick）
```

> 模块无存储 key / 日志 TAG 等常量需求（玩具状态不持久化、不写 Log），不设 LabConstant。

## 3. 公开 API

| 暴露点 | 消费方 | 说明 |
|--------|--------|------|
| `NavGraphBuilder.labGraph` | app 壳 MainActivity NavHost | 一行聚合 4 个页面 |
| `LabDeepLinkModule`（Hilt） | 壳层 DeepLinkRegistry | `@IntoSet` 贡献 host `lab` |
| `LabRoute` | 模块内自用 | 路由单一事实来源（feature 不反向依赖壳层 PageConstant） |

## 4. 依赖关系

- **本模块依赖**：`:common:utils`（LocalReduceMotion / pressable / HapticUtils / AnimationUtils）、`:common:ui`（清氧令牌 + PageHeader/AppButton）、lifecycle-viewmodel / hilt-navigation-compose / navigation-compose / compose-foundation / compose-animation / material-icons-extended
- **谁依赖本模块**：`:app`（壳层聚合 labGraph）
- **明确不依赖**：`:common:network`（模块内无网络）、`:common:database`（无存储）

## 5. 内部约定

1. **纯本地模块**：禁止新增 Retrofit/Room/DataStore 依赖；玩具状态（泡泡阵列、戳破计数、呼吸相位、鱼群）一律 ViewModel 内存态，退出页面即弃。
2. **模拟内核与 UI 分离**：`BoidsSimulator` 是零 Compose/零 Android 依赖的纯 Kotlin 类；Page 只负责 `withFrameNanos` 驱动 tick 与绘制。锚点单测（邻居查询等价暴力全扫 / 聚集收敛 / 冲击波径向 / 边界回转 / 确定性 / 限幅夹界等）**仅本地验证**——`src/test/` 不入库（共享规则：新增测试文件一律不提交），干净 checkout 上测试为空属预期，非缺失。
3. **动效**：只允许 `:common:utils` AnimationUtils vocabulary（defaultSpring/pressTween/DURATION_* 常量），禁止页面写魔法时长；**每个动效必须有 LocalReduceMotion 降级分支**（泡泡纸：戳破直接消失态；呼吸：圆环静止固定尺寸、文字与触觉保留；鱼群：默认静帧 + 用户主动播放/暂停开关，触觉保留）。
4. **触觉**：统一 `:common:utils` HapticUtils（performHaptic），reduced motion 下保留；泡泡三档力度跟泡泡大小档走。
5. **交互反馈**：禁 Material ripple/state layer，一律 `pressable`（PressFeedbackModifier 旧 pressScale/pressAlpha 写法无效，勿模仿）。
6. **颜色**：全部从 `:common:ui` 令牌取（v0.4：background=#F5F6F3、surfaceVariant=#EFF1EE、outline=#E6E9E6、textTertiary=#A9AEA9），Canvas 图形色同样引用令牌（water/waterSoft 系），禁止页面写死色值。
7. **文案**：用户可见文本一律进本模块 `res/values/strings.xml` 经 stringResource 引用；ViewModel 不持 UI 文案。
8. **数字显示**：计数/倒计时/数量槽位必须 tabular numbers（`fontFeatureSettings = "tnum"`）。
9. **鱼群帧预算**：tick 由 Page 的 `withFrameNanos` 驱动，单帧模拟耗时超预算自动降 tick 率（backoff delay），低端机不死循环烧电。

## 6. 已知坑

1. **ViewModel 必须 `hiltViewModel()` 获取**：全项目 `@HiltViewModel` + `@Inject constructor`（无无参构造），用 `androidx.lifecycle.viewmodel.compose.viewModel()` 会反射失败进页面即 FATAL。
2. **组合期禁止副作用**：导航、触觉、状态回写一律放 `LaunchedEffect`/`SideEffect`；鱼群 tick、呼吸 tick、pop 帧循环全部在 `LaunchedEffect` 内。
3. **`withFrameNanos` 循环退出条件**：无动画泡泡/页面不可见时循环必须能自然终止（以状态驱动 key），避免页面退出后空转。
4. **AudioTrack 静态模式播完需手动 release**：`PopSoundPlayer` 的合成/播放/释放在单线程执行器内完成，禁止在主线程 `write`（阻塞）。
