# GAME-THEME.md · 游戏主题化 Token 映射与决策记录

> 范围：`library/<gameKey>/index.html`（以目录实况为准）。
> 约束来源：`GAME-THEME-PROMPT.md` 三节（硬约束 / 主题变量基线 / 深色模式同步机制）+ 平台设计规范。

## 1. 色板来源决策（D1）

**以 Android `ui-design-spec.md` v0.4 为唯一色板来源，SmartWashWeb token 不混用。**

理由：游戏经管理后台上传 MinIO 后由 **App 内 WebView 消费**（SmartWashGameBridge 挂在
Android WebViewPage），视觉上下文是 App 而非 Web 后台；且 Android 规范自称「全应用 UI 唯一
约束来源」。Web token 的 primary 是靛蓝 `#6366f1`，与 App 品牌绿冲突，混用会出第三种绿。

v0.4 覆写优先：浅色 `background=#F5F6F3`、`surfaceVariant=#EFF1EE`、`outline=#E6E9E6`、
`textTertiary=#A9AEA9`（覆写 §6.5 浅色列的旧值）；深色列 v0.4 未触碰，取 §6.5。

## 2. 主题变量基线（结构层，所有游戏统一）

| Token | 浅色 | 深色 | 平台出处 |
|---|---|---|---|
| `--g-bg` | `#F5F6F3` | `#141614` | v0.4 background / §6.5 |
| `--g-surface` | `#FFFFFF` | `#1D201E` | surface |
| `--g-surface2` | `#EFF1EE` | `#262A27` | v0.4 surfaceVariant |
| `--g-primary` | `#2D9B6A` | `#2D9B6A` | primary（深色不变） |
| `--g-primary-deep` | `#1E8C5C` | `#5BC894` | primaryDeep（深色=文字强调提亮） |
| `--g-primary-soft` | `#E8F6EF` | `#1D3229` | primarySoft |
| `--g-on-soft` | `#0B3625` | `#B9E8CF` | onPrimarySoft |
| `--g-text` | `#1C1C1E` | `#E6E9E6` | textPrimary |
| `--g-muted` | `#6B6E73` | `#A9AEA9` | textSecondary |
| `--g-faint` | `#A9AEA9` | `#7E837D` | v0.4 textTertiary |
| `--g-border` | `#E6E9E6` | `#2C312D` | v0.4 outline / §6.5 outline |
| `--g-hairline` | `#EFF1EE` | `#262A27` | hairline |
| `--g-danger` | `#C9526B` | `#DB7186` | danger / §6.5 提亮 |
| `--g-warning` | `#D98A26` | `#E2A44E` | ongoing / §6.5 提亮 |
| `--g-success` | `#2D9B6A` | `#4ADE6E` | success / §6.5 done 提亮 |
| `--g-water` | `#4A9FD4` | `#6BB4DE` | water / 深色按「提亮一档」推导（D4） |
| `--g-scrim` | `rgba(245,246,243,.9)` | `rgba(20,22,20,.9)` | = bg 90% 透明（覆盖层用，D4） |
| `--g-shadow` | `0 2px 12px rgba(20,40,30,.05)` | `0 2px 12px rgba(0,0,0,.28)` | §2.4 两档阴影之卡片档 |
| `--g-radius` | `16px` | 同 | radiusCard（唯一卡圆角） |
| `--g-radius-btn` | `14px` | 同 | radiusButton |
| `--g-radius-inner` | `12px` | 同 | radiusInner |
| `--g-gap` | `16px` | 同 | 卡片间距 |

正文对比度：`--g-text`/`--g-muted` 对各自 bg/surface 均 ≥4.5:1（平台已验证的配对）。

预算对齐注记（平台规范优先，写法从省）：
- 标题（h1/h2/覆盖层标题）用 `--g-text`——平台规范标题=主文本色，品牌色只做强调；
  `--g-primary-deep` 留给状态/数据强调（如 gomoku 状态行、breakout 计分数字）。
- `--g-on-primary` 恒为 `#FFF`：按钮白字允许直接写字面 `#fff`（token 语义保留在本表）。
- 次按钮：规范= surfaceVariant 底；预算紧的文件可等价换「surface 底 + 1px 描边」描边次按钮。

## 3. 游戏素材色决策（D2）

**玩法素材（砖块彩虹、小鸟、棋子黑白、管道绿、地面对比）保留各游戏原设计值**——
这是玩法辨识度，不是 UI 壳。但两类例外必须跟随主题：

1. **铺面面积素材**（大面积铺底、深浅下刺眼或不可见的）：sky 渐变、棋盘木纹、地面带、
   蛇场底、云透明度 → 定义 `--g-<game>-*` 素材 token，深色给等色相压暗变体
   （推导方式：原色 HSL 保持色相，明度压到深色底可容纳区间，写进下方各游戏表）。
2. **语义色**（危险点、错旗、食物红）→ 直接换 `--g-danger` 等结构 token。

所有 canvas 颜色（含素材）一律经文件顶部 `PAL` 对象读 CSS 变量（getComputedStyle），
JS 里零颜色字面量（白色高光/瞳孔等恒白小元素除外，见各游戏表）。DOM/SVG 侧直接
`var(--g-*)`，其中内联 SVG 的 fill/stroke 用 `var()` 引用，随主题自动生效无需重渲染。

### 各游戏素材 token

| 游戏 | 素材 token（浅色 = 原值） | 深色变体 |
|---|---|---|
| snake | `--g-field` `#FFFFFF`（原 #ecf2df 提为卡面）、`--g-grid` `#EFF1EE`、`--g-snake-head` `#1E8C5C`、`--g-snake-body` `#2D9B6A`、`--g-food` `#C9526B` | head `#5BC894`、body 不变（选中底不变语义）、food `#DB7186`、field `#1D201E`、grid `#262A27` |
| breakout | `--g-sky1/--g-sky2` `#F5F6F3→#EFF1EE`（bg/surface2 直用）、砖块 `PALETTE` 8 色保留字面、`--g-ball` `#FFF`、`--g-ballln`、拍面读 `--g-primary`/`--g-primary-deep` | sky `#1D201E→#141614`；拍面品牌渐变随 deep（深色端提亮）；砖块不变（中高明度，暗底可见） |
| flappy | `--g-sky1/--g-sky2` `#BFE7FB→#EAF8FF`、`--g-cloud` `rgba(255,255,255,.85)`、`--g-gnd`/`--g-gnd2` `#E8D9A6/#D9C683`、`--g-gnd-line` `#7CB85C`；管道/小鸟保留字面 | sky `#232B31→#181D21`、cloud `rgba(255,255,255,.25)`、gnd `#4A4433/#3E3929`、gnd-line `#5E7A48`；管道绿/小鸟黄不变（暗底可见） |
| gomoku | `--g-bd1/--g-bd2` `#F6DCAB→#ECCF92`、`--g-line` `#7A5B34`、`--g-star` `#5A4325`、`--g-shadow`=浮层档 `0 10px 24px rgba(20,40,30,.10)`；棋子黑白保留字面 | bd `#4A3D26→#3A2F1D`、line `#9A845C`、star `#B49C6E`、shadow `rgba(0,0,0,.45)`；末手标记 `--g-danger` |
| magic-tower | `--g-mt-wall/--g-mt-floor/--g-mt-grid`（塔内墙/地板/格线，深色同色相压暗）；怪物/钥匙/药水/宝石等绘色全部读 `--g-success/--g-warning/--g-danger/--g-water/--g-muted/--g-text`（2026-11-07 登记） | mt-wall `#2A332E`、mt-floor `#1D201E`、mt-grid `#2C312D` |
| reversi | `--g-felt/--g-felt2` `#2D9B6A→#1E8C5C`（品牌绿毛毡）、`--g-line` `#157A50`；棋子用 `--g-surface`/`--g-text`（恒白/恒暗） | felt `#1E4D3A→#173D2D`、line `#0B241A`；末手标记 `--g-danger` |
| minesweeper | 纯 DOM；数字 n1–n8 保留原值（浅色），深色逐个提亮（见该文件 CSS）；旗/雷 SVG 色改 `var(--g-danger)` / `var(--g-text)` 系 | 面板底 `--g-surface2`、翻开格 `--g-bg`、盖格渐变用 surface 系 |
| billiards | `--g-felt/--g-felt2` `#2F8A66/#379871`、`--g-rail` `#C9B691`、`--g-pocket` `#1A221D`；`BALL_COLORS` 8 色保留字面（球号辨识素材，2026-11-06 补登） | felt `#27604A/#2E6E55`、rail `#4E4636`、pocket `#0B0E0C`；BALL_COLORS 不变（中高明度） |
| snow-trail | `--g-st-sky1/--g-st-sky2`（天空渐变）、`--g-st-snow1/--g-st-snow2`（雪面）、`--g-st-peak/--g-st-peak2`（山脊）（2026-11-06 补登，前缀 `st-`） | 深色为同色相压暗变体，见该文件 CSS |
| moon-lander | `--g-sky1/--g-sky2`（天空）、`--g-moon/--g-moon2`（月面）（2026-11-06 补登） | sky `#1B242E→#141614`、moon `#3B3E36/#2F322B` |

**canvas 内少量功能性 rgba 字面（D2 例外补登，2026-11-06）**：breakout 砖块损伤
`rgba(50,40,60,…)` 与砖块描边 `rgba(0,0,0,.14)`（损伤随 hp 递增、描边恒暗）；
gomoku 白子描边 `rgba(90,80,60,.55)`（恒亮小元素描边，与恒白高光同类）。

## 4. 深浅色三层机制实现决策（D3）

**CSS 只写两块：`:root` 浅色 + `[data-theme=dark]` 深色覆盖；不写 `@media
(prefers-color-scheme)` CSS 层，也不监听 matchMedia（D5 修订二）**。主题由运行时决定
（**桥唯一控制，无桥恒浅色**）：

- **运行时置于 `<head>`（先于首帧执行）**：初始化读 `getTheme()`，具体值即写
  `data-theme`；`'system'`/空串/无桥 → **恒浅色**。
- **无手动切换层**：不写 localStorage `g-theme`，页面内无切换按钮（D5 修订，2026-10-31 用户裁定）。
- **深色只能由桥进入**：App 推送 `__applySmartWashTheme(mode)` 生效；canvas 重绘经
  `paint() → __gOnTheme` 钩子由推送驱动（PROMPT 的 matchMedia 重绘路径随本层裁撤）。

**裁定理由（2026-10-31 用户）**：Android WebView 的 `prefers-color-scheme` 检测跨
厂商/版本行为有差异，不可依赖——主题只信桥，确定性优先。事实备注：Chromium 83+ 的
WebView 技术上支持该媒体查询，但本合集 H5 一律不读它。

**连带影响（App 侧待定）**：当前 App `getTheme()` 恒返回 `'system'`（无应用内开关），
具体主题靠 `onPageFinished`/uiMode 推送 → 深色模式下开游戏会**先浅色、加载完才变深**
（短暂闪烁）。消除方案：App 侧让 `getTheme` 直接返回 `resolveCurrentTheme(uiMode)`
（feat/game-center 一行级改动，待用户拍板）。

### 桥协议（与 feat/game-center `GameThemeBridge.kt` 对齐，勿改动）

- `window.SmartWashGameBridge.getTheme()` → `'light' | 'dark' | 'system' | ''`；
  具体值直接生效；`'system'`/空串/无桥 → 浅色。
- `window.__applySmartWashTheme(mode)`（mode 为具体 `'light'/'dark'`，其他值忽略）：
  记录为最近桥值并生效；深色模式的唯一入口。
- ~~localStorage `g-theme`~~：已随手动层裁撤，不再读写。

### 运行时（每文件内联一份，单文件自包含约束；`<head>` 内 `</style>` 之后）

```js
(function(){
var R=document.documentElement,B=null;
function paint(m){R.dataset.theme=m;var c=document.querySelector('meta[name=theme-color]');
  if(c)c.content=getComputedStyle(R).getPropertyValue("--g-bg").trim();
  try{window.__gOnTheme(m)}catch(e){}}
window.__applySmartWashTheme=function(m){if(m==="light"||m==="dark"){B=m;paint(m)}};// App 推送
try{var t=SmartWashGameBridge&&SmartWashGameBridge.getTheme();if(t==="light"||t==="dark")B=t}catch(e){}
paint(B||"light");
})();
```

> 实现说明：`__gOnTheme` 未定义时经 try/catch 空转；`meta[name=theme-color]` 缺失时
> 跳过同步（该 meta 建议补齐）。

## 5. ~~手动切换按钮~~（D5 · 2026-10-31 两次修订）

**初版实现了右上角 44px 手动切换按钮 + `g-theme` 记忆层；用户当日裁定撤销**：
游戏主题**只能由桥控制**，页面内不提供切换入口。第一次修订裁撤按钮与 `g-theme`
（无桥时按 matchMedia 跟随系统）；**第二次修订（同日）进一步裁撤 matchMedia 层——
无桥恒浅色，深色只能由桥推送进入**（理由与连带影响见 §4）。已从 5 个文件移除按钮
HTML/CSS、`__gThemeToggle`、`g-theme` 读写、matchMedia 监听及按钮让位布局。

保留项：`meta[name=theme-color]` 由 `paint()` 实时同步 `--g-bg`（5 个文件均有，
浅色默认值 `#F5F6F3`）。

> 初版实现期间的实测教训，留档备查：主题切换基准必须用**当前生效模式**而非手动值
> （否则跟随系统时点击无效）；SVG 数值属性必须加引号（`r=4.4/` 会把自闭合斜杠吞进值）。

## 5.1 原生 chrome 控制（D6 · 2026-11-02）

**问题**：游戏在 App 内 WebView 里运行，上方是 App 原生 `PageHeader`（标题 + 返回箭头）。
各游戏历史上又自绘了一个 `<h1>` 大标题，两条标题栏叠在一起，与系统元素不协调。

**两条决策**：

1. **标题唯一来源 = 原生页头**。游戏**不再自绘标题**：`<h1>` 一律删除（含孤立的 `h1{}` 规则），
   页头显示的一直是后端下发的游戏名（`GameItem.title`）。游戏 `<header>` 只保留**功能性**内容
   （记分块 `.scores`/`.boxes`、状态行 `#status`、玩法提示 `.sub`、操作按钮）。
2. **原生 chrome 可由 H5 控制**（桥 + URL 参数双通道）：

| mode | 原生标题返回栏 | 系统状态栏 | 适用 |
|---|---|---|---|
| `full`（默认） | 显示 | 显示 | 卡片式布局游戏（有 `.wrap`） |
| `immersive` | 显示 | **隐藏** | 全屏画布游戏（`html,body{height:100%}` + `overflow:hidden`） |
| `none` | **隐藏** | **隐藏** | 页面自绘 chrome（需自备返回入口） |

- **桥**：`window.SmartWashGameBridge.setChrome(mode)`，仅白名单 host（后端 API + MinIO endpoint）
  内生效，非法值经 `GameChrome.normalize` 回落 `full`（脏值不得让页面丢掉返回入口）。
- **URL 参数**：路由 `WebView?url=&title=&chrome=`，供原生侧在**加载前**给定初值，避免先闪一帧原生栏。
- **离开页面复原**：`WebViewContent` 的 `DisposableEffect` 在 onDispose 时把 chrome 复原为 `full`，
  沉浸式不会跟着导航带进后续页面。

**当前接入 `immersive` 的 5 个全屏画布游戏**：breakout、flappy、gomoku、falling-sand、beat-tap。
其余 11 个保持 `full`。

## 5.2 游戏数据桥（D7 · 2026-11-09，PR #89）

**问题**：游戏存档存 H5 localStorage，换设备/清缓存即丢；无账号维度成绩，无法做排行榜；
关卡内联在 `index.html`，扩展关卡要重传整个游戏包。

**方案 = 桥代理**：与 `getTheme`/`setChrome` 同一注入对象 `window.SmartWashGameBridge`，
新增 5 个数据方法。**H5 不持有 token**——原生侧带 `Bearer` 请求后端 `/web/auth/game/*`
（ROLE_USER，契约源 `SmartWash/src/main/java/com/smartwash/game/controller/WebGameDataController.java`），
结果经 `evaluateJavascript` 回传。所有方法过 host 白名单（复用主题桥 `isAllowedHost`），
**白名单外 fail-closed**（回调 `ok=false`，不泄露任何数据）。

### 方法签名（原生 `GameThemeBridge.kt` 的 `@JavascriptInterface`，勿改动）

| 方法 | 参数 | 后端端点 | 返回 payload |
|---|---|---|---|
| `loadState` | `(gameKey, cbId)` | `GET /web/auth/game/state` | `{gameKey, stateJson(可空), schemaVer, updatedAt}`，无存档 `stateJson=null` |
| `saveState` | `(gameKey, payloadJson, cbId)` | `PUT /web/auth/game/state` | 同上（upsert 后的存档） |
| `reportScore` | `(gameKey, payloadJson, cbId)` | `POST /web/auth/game/score` | 字符串（历史最佳说明文案） |
| `getLeaderboard` | `(gameKey, limit, cbId)` | `GET /web/auth/game/leaderboard` | `{gameKey, metricLabel, metricOrder, entries:[{rank,userId,maskedPhone,avatar,metricValue,metricExtra}], my:{rank,metricValue}}` |
| `getLevels` | `(gameKey, cbId)` | `GET /web/auth/game/levels` | `{gameKey, version, levels:[{levelNo,name,dataJson,difficulty,sort}]}` |

### 载荷与回调协议

- **回调**：`window.__gOnGameBridgeResult(cbId, ok, payload)`——`ok=true` 时 `payload` 为
  上表 JSON 对象字面量；`ok=false` 时为失败说明（`非白名单来源` / `桥未就绪` / `网络异常` /
  后端 message）。cbId 由 H5 生成（GB 适配层用 `gb<时间戳>_<seq>`），原生原样回传。
- **saveState 载荷双格式兼容**（2026-11-09 修复所有游戏存档静默失败的兜底）：
  标准 `{"stateJson":"<游戏自定义 JSON 字符串>","schemaVer":1}`；裸 JSON（无 `stateJson` 键）
  整体作为存档内容落库。游戏侧一律走 GB 适配层包装标准格式。
- **reportScore 载荷**：`{"metricValue":100,"metricExtra":null,"metricJson":null}`——
  语义由 `game_config` 决定（desc 分数越大越好 / asc 越小越好），榜单开关/列名同为配置。
- **getLeaderboard 的 `limit`**：`<=0` 时原生归一化为 20。
- **关卡缓存策略**：启动拉 `getLevels` 全量（KB 级），按 `version` 比对 localStorage 缓存，
  版本一致直接用缓存；包内保留默认关卡作离线兜底（接口失败游戏仍可启动）。
- **存档策略**：双写（localStorage + 账号），读时账号优先、本地兜底；冲突以账号为准。

### H5 侧 GB 适配层（每游戏内联一份，单文件自包含）

`var GB=(function(){...})()`：维护 `cbId → Promise` 映射表，桥缺失/方法不可用/回调 `ok=false`
一律 reject，游戏逻辑 catch 后**降级 localStorage**（无桥环境可玩，进度不丢）。
标准形态见 `sokoban/index.html`「游戏桥适配层」段，新游戏直接照抄。

## 6. 新游戏接入清单（library/ 后续加游戏时照此执行）

1. 抄第 2 节 token 两块 CSS（`:root` + `[data-theme=dark]`）+ 第 4 节运行时（置于
   `<head>`，**无按钮、无 matchMedia**，见 D5 修订）。**token 按需定义**：该文件实际
   引用的才写进 CSS（取值一律以第 2 节表为唯一来源，不许新造）；深色块只写与浅色
   **不同**的 token，相同值靠级联继承。
2. DOM 颜色 → `var(--g-*)`；canvas 颜色收进 `PAL` 对象，`palLoad()` 读
   `getComputedStyle`，并定义 `window.__gOnTheme = function(){ palLoad(); <该游戏重绘函数>() }`。
   rAF 连续重绘的游戏只需在钩子里刷新缓存梯度对象。
3. 存档键不改；`touch-action`/`user-select`/`touch-callout` 既有规则不回退。
4. **不自绘标题**：不要在 `<header>` 里写 `<h1>`——标题由 App 原生页头唯一承载（§5.1）；
   游戏 `<header>` 只放记分/状态/玩法提示/操作按钮。
5. **字号入八档**（36/28/22/18/16/14/12，weight 仅 400/500/600/700），圆角只用
   16/14/12/10（见 §2 与 Android `ui-design-spec.md` v0.4）。
6. 全屏视口游戏（`html,body{height:100%}` + `overflow:hidden`）在 `<head` 主题运行时里追加
   `try{SmartWashGameBridge&&SmartWashGameBridge.setChrome('immersive')}catch(e){}`（§5.1）；
   卡片式布局游戏不加。
7. **接入数据桥 GB 适配层**（§5.2）：内联 GB Promise 包装（照抄 `sokoban/index.html` 标准形态），
   存档双写（账号优先 + 本地兜底）、有关卡的游戏异步加载 + `version` 缓存 + 包内默认关卡兜底、
   有成绩的上报 `reportScore`；桥失败/无桥一律降级 localStorage，游戏可玩不报错。
8. 过三关：`node` 提取 `<script>` `new Function` 语法检查；`grep -nE 'https?://'` 零命中；
   375px 视口实开无横向溢出。体积：改后 ≤ 改前 × 1.3。
9. 更新 `PROGRESS.md` 该游戏行。

## 7. 三关验证与预算

- 语法：`awk` 提取 `<script>…</script>` → `node -e "new Function(...)"`。
- 外链：`grep -nE 'https?://|//[a-z0-9.-]+\.(com|cn|net|org)' <file>`（应零命中）。
- 375px：browser 实开（同时抽查深浅切换 + 桥函数模拟）。
- 预算与状态见 `PROGRESS.md`（逐文件记录改前/改后字节数与增幅）。
