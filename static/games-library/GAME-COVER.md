# GAME-COVER.md · 游戏封面制作规范与管线存档

> 生效范围：`games-library/<gameKey>/<gameKey>.png`（800×450 封面）与
> `SmartWash-Android/common/ui/src/main/res/drawable-nodpi/game_<key>.png`（同图副本，key 中 `-` → `_`）。
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
| ⑥ 部署 | cp ×2 | games-library + drawable | 两处同图；文件名规则见开头 |

## 3. 硬性检查（新封面过五关）

1. 尺寸恰好 800×450（`sips -g pixelWidth -g pixelHeight`）。
2. 体积 ≤ 120KB（超了先降颗粒强度/色板数，不要动主体）。
3. 视觉自查：主体完整居中、表情到位、无空白/纯黑/元素重叠、渐变无断层（量化后目检一遍）。
4. 两处落位同名同步（`<key>.png` 与 `game_<key>.png`），不要只更新一处。
5. SVG 源归档到 `covers-src/`，保证封面可再编辑（改版不必从头重画）。

## 4. 已知坑

- **PS `Add Noise` 后 PNG 体积爆炸**：2.5% 高斯单色噪声 + 128 色量化 ≈ 280KB（1.2% 也有 200KB）；
  统一改用 ffmpeg `noise` 滤镜（同样的视觉颗粒感，量化后 31KB）。
- Chrome headless 渲染 SVG 时 `--window-size` 必须与 SVG 尺寸一致，否则出现边距或裁切。
- `save_for_web` 导出的 PNG 默认 PNG-24；扁平插画务必再过一遍调色板量化。
- 封面是 nodpi 位图直接进 APK：19 张 × 体积 ≈ 直接计入包体，单张预算从严。

## 5. 排期备注

- **2026-11-14 批次（用户裁定：全量重构）**：50 款封面全部按本规范重绘。
  - suika：管线验证样张，已完成并部署（31KB）。
  - 19 款新游戏（asteroids…xiangqi）：主体自由设计。
  - 31 款存量（snake…tetris）：主体沿用各自旧封面的可识别元素，按新规范升级执行（层次/光影/表情）。
  - SVG 源全部归档 `covers-src/`；每张过 PS Unsharp+目检 → ffmpeg 轻颗粒+128 色量化 → 双处部署。
