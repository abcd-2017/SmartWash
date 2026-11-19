# GAME-COVER.md · 游戏封面制作规范与管线存档

> **定版决策（2026-11-15，用户拍板）**：游戏封面采用**方案 A（家族风·场景插画：粗描边 + 签名表情 + 三层背景）**。
> 选型历程：B/C/D（自造风格）被否 → E（TikTok 深底）/G（Spotify 深底）/F（X 极简）/H（Apple 图标语言）/I（A+H 融合）六案对比后，
> 确认 A 最适合「游戏内容封面」场景（角色叙事 + 场景感为内容消费设计；列表扫读性损失可接受，详情页大图是第二展示位）。
> 深底方案（E/G）弃用（与清氧浅界面冲突）；H/I 留档备查（适合工具标识而非内容封面）。

> 生效范围：`games-library/<gameKey>/<gameKey>.png`（800×450 封面，Web 后台「选择游戏目录导入」时自动识别上传）。
> **2026-11-16 用户裁定：取消 Android 本地兜底副本**——封面统一走服务端下发（MinIO），
> 端上缺图显示默认兜底图；`drawable-nodpi` 与 `LocalCoverArt` 不再新增映射。
> 首版制定：2026-11-14（随 19 款新游戏封面重构落地）。范例成品：`suika/suika.png`（31KB）。

## 1. 家族风格（与存量 31 张封面一致的视觉语言）

1. **粗描边卡通**：主体形体统一深色描边（暖色世界 `#3A2A1E`、冷色/夜色世界 `#1E2620` 一类），
   主形体 stroke-width 8–10（800×450 画布下），次级 5–7；`stroke-linejoin="round"`。
2. **家族签名表情**：画面主视觉必须带「两圆眼 + 弧线嘴」的极简表情（参考 snake 的蛇脸、
   suika 的西瓜脸）——这是合集的角色记忆点。
3. **构图**：主体占画面 55–70%，重心略偏下居中；地面投影大椭圆（深色 30–40% 透明度）。
4. **背景三层**：纵向 2–3 档渐变（色系随各游戏现有封面）→ 底部深一档地面带 →
   星尘点缀（白色 plus 十字 + 小圆点，opacity 0.4–0.6）→ 四角轻 vignette（黑 25–30%）。
5. **质感细节**：每主形体一处左上白色高光（实心小圆或 radialGradient，opacity 0.3–0.5）；
   玻璃/水元素用半透明白渐变。扁平为底、光影为辅，不做写实。
6. **封面无文案**：不写游戏名/标语（列表场景封面下方自带标题）；棋子上的汉字（「万」「車」等）属于内容除外。
7. 色相克制：封面背景色相与游戏内主题 token（GAME-THEME.md §2/§3）同族，禁止引入第三套品牌色。

## 2. 制作管线（SVG → 渲染 → 精修 → 压缩）

| 步骤 | 工具 | 产物 | 要点 |
|---|---|---|---|
| ① 矢量精绘 | 手写 SVG（源文件存 `covers-src/<key>.svg`） | 800×450 SVG | 自包含、零外链、无滤镜依赖的复杂特性 |
| ② 渲染 | Chrome headless | `<key>-base.png` | `--headless=new --screenshot=… --window-size=800,450 --hide-scrollbars` |
| ③ 精修 | Photoshop MCP | 质感版 PNG | Unsharp Mask（60% / 0.6px / 阈值2）+ 逐张目检；需要时手工调色/修形 |
| ④ 颗粒 | ffmpeg `noise=alls=6:allf=t+u` | 颗粒版 | 轻颗粒（≈2.3%）——**不要用 PS 加颗粒**（高斯噪声不可量化，PNG 体积 ×6） |
| ⑤ 压缩 | ffmpeg palettegen/paletteuse | `<key>.png` | `palettegen=max_colors=128` + `paletteuse=dither=none`；目标 ≤ 120KB（基线 85–95KB） |
| ⑥ 部署 | 归档 | `games-library/<key>/<key>.png` | 单处落位；上传经 Web 后台「选择游戏目录导入」自动识别（2026-11-16 起取消 drawable 副本） |

> **本机无 ffmpeg 时的等价实现**（2026-11-16 验证通过，产出自 suika 校准）：
> ③ Pillow `UnsharpMask(radius=0.6, percent=60, threshold=2)`；
> ④⑤ Pillow `quantize(colors=128, method=MEDIANCUT, dither=NONE)` + 索引层颗粒 + `oxipng -o 4`。
> 颗粒必须约束在调色板内（量化后扰动颜色索引），**不要**走 RGB 高斯噪声——会让量化体积 ×5 以上（见 §4）。
>
> **2026-11-19 修订（索引层颗粒的正确做法）**：MEDIANCUT 128 色板含大量间距 ≤3 的近重复色，
> 直接逐像素扰动索引会让像素在视觉相同色之间横跳（PNG 熵爆炸，±6 噪声实测 184–214KB）。
> 正确配方：**先合并色板内切比雪夫距离 ≤5 的近重复项（palettegen 按覆盖度合并出「分得开」色板的等价行为）
> → RGB 加 ±6 均匀噪声 → 逐像素映射色板最近色（paletteuse dither=none 等价）**。
> 噪声只在跨量化边界处翻转（暗角/色板稀疏区翻转更多），颗粒分布与 ffmpeg 一致，
> 成品体积即回到 67–93KB 区间。

## 3. 硬性检查（新封面过五关）

1. 尺寸恰好 800×450（`sips -g pixelWidth -g pixelHeight`）。
2. 体积 ≤ 120KB（超了先降颗粒强度/色板数，不要动主体）。
3. 视觉自查：主体完整居中、表情到位、无空白/纯黑/元素重叠、渐变无断层（量化后目检一遍）。
4. 仅落 `games-library/<key>/<key>.png` 一处；**不要**再同步 drawable-nodpi / LocalCoverArt（2026-11-16 用户裁定，取消本地兜底）。
5. SVG 源归档到 `covers-src/`，保证封面可再编辑（改版不必从头重画）。

## 4. 已知坑

- **PS `Add Noise` 后 PNG 体积爆炸**：2.5% 高斯单色噪声 + 128 色量化 ≈ 280KB（1.2% 也有 200KB）；
  统一改用 ffmpeg `noise` 滤镜（同样的视觉颗粒感，量化后 31KB）。
- Chrome headless 渲染 SVG 时 `--window-size` 必须与 SVG 尺寸一致，否则出现边距或裁切。
- `save_for_web` 导出的 PNG 默认 PNG-24；扁平插画务必再过一遍调色板量化。
- ~~封面是 nodpi 位图直接进 APK：19 张 × 体积 ≈ 直接计入包体，单张预算从严~~（2026-11-16 起取消本地兜底副本，封面不再计入包体；体积预算只影响下发流量与存储）。

## 5. 排期备注

- **2026-11-14 批次（用户裁定：全量重构）**：50 款封面全部按本规范重绘。
  - suika：管线验证样张，已完成并部署（31KB）。
  - 19 款新游戏（asteroids…xiangqi）：主体自由设计。
  - 31 款存量（snake…tetris）：主体沿用各自旧封面的可识别元素，按新规范升级执行（层次/光影/表情）。
  - SVG 源全部归档 `covers-src/`；每张过 PS Unsharp+目检 → ffmpeg 轻颗粒+128 色量化 → 双处部署。

- **2026-11-16 批次（第十批新游戏 10 款）**：ballz-bounce / bomberman / flow-connect / matchstick /
  mini-golf / peg-solitaire / penalty / poem-fill / riddle / spider-solitaire。
  - 主体按家族风自由设计（粗描边 + 签名表情 + 三层背景 + 地面带 + 星尘 + vignette），SVG 源归档 `covers-src/`。
  - 本机无 ffmpeg，走 §2 的等价管线；体积 74–94KB、128 色，全部 ≤120KB。
  - 落位：仅 `games-library/<key>/<key>.png`（曾短暂同步 drawable-nodpi + LocalCoverArt 映射，同日按用户裁定撤除——封面只走服务端下发，缺图显示端上默认兜底）。

- **2026-11-19 批次（第十批 10 款封面 PS MCP 精修补齐）**：第十批当时因本机无 ffmpeg 走等价管线
  （Pillow Unsharp 替代 PS），本次按用户要求补齐主管线第③步并重做 ④⑤：
  SVG 源重渲染（Chrome headless 800×450）→ **PS 2026 MCP Unsharp Mask（60% / 0.6px / 阈值 2）逐张执行**
  → 按 §2 修订版等价配方重做颗粒+量化 → 逐张旧/新对比目检（主体、家族表情、星尘、暗角颗粒、无断层）。
  体积 67–93KB、精确 800×450、色板合并后 20–40 色，全部 ≤120KB；仅落 `games-library/<key>/<key>.png` 一处。
