# AGENTS.md — feature:divination（观象台占卜子系统）

## 1. 模块身份

- **Gradle 坐标**：`:feature:divination`
- **包名**：`com.smartwash.feature.divination`
- **职责**：校园洗衣平台「观象台」占卜子系统——四套术数算法内核（六爻/梅花/奇门/六壬）纯离线排盘 + 卦历独立 Room 库 + LLM 解读网关 + 7 页面 UI，经 app 壳 Hilt 聚合对外暴露页面 composable 与 Repository。

## 2. 包结构

```
com.smartwash.feature.divination
├── DivRoute.kt                      # 路由常量密封类（7 路由，模块内单一事实来源）
├── core/                            # 算法内核（纯 Kotlin，零 Android 依赖，可单测）
│   ├── DivinationCommon.kt          # DivMethod/DivCategory/Trigram/WuXing 枚举
│   ├── GanZhi.kt                    # 天干地支
│   ├── Yao.kt                       # 爻
│   ├── liuren/LiuRenChart.kt        # 六壬排盘
│   ├── liuyao/{LiuYaoChart,LiuYaoFacts,LiuYaoTables}.kt  # 六爻排盘
│   ├── meihua/MeiHuaChart.kt        # 梅花排盘
│   └── qimen/QiMenChart.kt          # 奇门排盘
├── data/                            # 数据层
│   ├── DivChartCodec.kt             # 排盘结果 JSON 编解码（Gson）
│   ├── DivLlmRepository.kt          # LLM 解读网关（本期 Mock，后端就绪后切 DivinationApi）
│   └── DivRecordRepository.kt       # 卦历仓库（内存计算 → Room，复盘/追问读库不重算）
├── database/                        # 独立 Room 库
│   ├── DivinationDatabase.kt        # @Database(version=1, exportSchema=false)
│   ├── DivRecordEntity.kt           # div_records 表（单用户，不关联 userId）
│   └── DivRecordDao.kt              # 卦历 CRUD + Flow 观察
├── di/
│   └── DivinationModule.kt          # Hilt 供给：DivinationDatabase + DivRecordDao + DivinationApi
├── network/
│   └── DivinationApi.kt             # Retrofit 接口（interpret/followUp，@RequireAuthorization）
└── ui/
    ├── DivinationGraph.kt           # NavGraphBuilder.divinationGraph 路由聚合
    ├── DivinationScreen.kt          # 观象台入口（已迁至 DivHomePage）
    ├── components/                  # 共享 UI 组件
    │   ├── CoinAndMeridian.kt       # 铜钱/经络起卦
    │   ├── CompassDial.kt           # 罗盘
    │   ├── DivCommon.kt             # 公共 UI 工具
    │   ├── MethodPans.kt            # 术数选择盘
    │   └── PanComponents.kt         # 排盘渲染组件
    └── page/
        ├── ask/{DivAskPage,DivAskViewModel}         # 问事页
        ├── cast/{DivCastPage,DivCastViewModel}       # 摇卦/起局页
        ├── chart/{DivChartPage,DivChartViewModel}    # 排盘结果页
        ├── followup/{DivFollowUpPage,DivFollowUpViewModel}  # 追问页
        ├── history/{DivHistoryPage,DivHistoryViewModel}     # 卦历页
        ├── home/{DivHomePage,DivHomeViewModel}              # 观象台主页
        └── reading/{DivReadingPage,DivReadingViewModel}    # 解读页
```

## 3. 公开 API

| 暴露点 | 消费方 | 说明 |
|--------|--------|------|
| `DivRoute` | app 壳 PageConstant / HomePageConstant | 7 路由常量（Home/Ask/Cast/Chart/Reading/FollowUp/History） |
| `NavGraphBuilder.divinationGraph()` | MainActivity | 路由聚合扩展，一行完成注册 |
| `DivRecordRepository` | app 壳 / 主页 | 卦历读写（Flow 观察 + suspend 写入） |
| `DivLlmRepository` | DivReading/FollowUp VM | 解读/追问（本期 Mock） |

## 4. 依赖关系

- **本模块依赖**：`:common:utils`（ApiResult/RequestState）、`:common:network`（Retrofit + 拦截器）、`:common:ui`（DivinationColors + AppConfirmDialog）、`:common:utils`（触感/按压，implementation 不传递须显式声明）、`:common:database`（Room runtime/ktx 经 api 传递）、`libs.lunar`（历法底座）、`libs.gson`（卦盘编解码）
- **谁依赖本模块**：`:app`（壳层聚合页面 composable + 消费 DivRecordRepository）
- **独立数据库**：DivinationDatabase 自持 ksp 处理器（与 common:database 编译 AppDatabase 同模式），不反向依赖 common:database 模块代码

## 5. 内部约定

1. **算法内核纯 Kotlin**：`core/` 下零 Android 依赖，保证可单元测试；所有排盘数据类为纯数据，Gson 可直接反射。
2. **路由单一事实来源**：模块内跳转引用 `DivRoute`，绝不反向依赖 app 壳 `PageConstant`；app 侧 `PageConstant.Div*` / `HomePageConstant.Divination` 委托其值。
3. **排盘即入库**：摇卦成卦走「内存计算 → Room」，复盘/追问一律读库中原盘原时刻（`castAt`），不重算。
4. **四柱/节气走 lunar 库**：`DivRecordRepository` 直接消费 `com.nlf.calendar.Solar`，禁止自行实现历法。
5. **鉴权走拦截器**：`DivinationApi` 标注 `@RequireAuthorization`，鉴权/错误转译由 common:network 拦截器自动处理，本模块不手动注入 token。
6. **解读本期 Mock**：`DivLlmRepository` 返回内置 Mock 文本（600ms 模拟延迟），后端网关就绪后切 `DivinationApi` 真连实现，VM 侧用 `RequestState` 管理网络态。

## 6. 已知坑

1. **Room ksp 自持**：`common:database` 的 Room 依赖经 api 传递，但 ksp 处理器必须本模块自持编译 `DivinationDatabase`；删除会导致运行时 `databaseBuilder` 找不到实现类。
2. **exportSchema = false**：独立库关闭 schema 导出，迁移时需手动比对 `div_records` 表结构；旧 `smartwash_db` 存量不迁移（demo 可接受）。
3. **DivLlmRepository Mock 期**：`DivinationApi` 接口已定义但未注入实现，调用 `interpret/followUp` 会走 Mock；后端联调前勿删 Mock 分支。
4. **lunar 库历法精度**：节气/中气计算依赖 `com.nlf.calendar`，跨年/闰月边界需回归测试；`castAt` 复盘不重算，但入库时刻 `createdAt` 取 `System.currentTimeMillis()`。
5. **四套算法锚点单测**：`src/test/` 下 liuren/liuyao/meihua/qimen 四套 `*AnchorTest.kt`，任何算法改动必须先跑对拍。
