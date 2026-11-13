# 小行星（gameKey：`asteroids`，category 建议：`arcade`）

- 玩法：经典小行星——驾驶飞船在太空中惯性飞行，射击击碎小行星（大碎中、中碎小、小消失），清空本关全部波次即可过关；被小行星撞上损失生命，生命耗尽则失败。每关波次数递进（1→8 波），波次越高小行星越多、越快。
- 操作：屏幕下方虚拟按键——按住「⟲ 左转 / ⟳ 右转」旋转，「▲ 推进」沿船头加速（松开后带惯性阻尼滑行），「● 射击」发射子弹（可按住连发）；键盘可用 ←/→ 旋转、↑/W 推进、↓/S 刹车、空格射击。右上角「暂停」。
- 说明：单文件自包含 H5，CSS/JS 全内联，零外链、零素材（canvas 自绘）。包内 12 关默认关卡，启动时经 `GB.getLevels("asteroids")` 拉取 DB 下发关卡（任一条非法整批放弃，回退本地缓存/包内默认）；进度与最高分经 `saveState` 持久化，成绩经 `reportScore` 上报。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片 / 音频
- [x] 主题：`:root` + `[data-theme=dark]` 两块 token，运行时置于 `<head>`（桥唯一控制，无按钮、无 matchMedia）
- [x] canvas 颜色全部经 `PAL` 读取 CSS 变量，`__gOnTheme` 接 `palLoad()` 重绘
- [x] 全屏画布布局（`html,body{height:100%}` + `overflow:hidden`），已接 `setChrome('immersive')`
- [x] 触屏热区：虚拟按键 64×52px（≥44px），支持多指同时按住（旋转+推进+射击）
- [x] 存档键规范：`sw.asteroids.v1` / `sw.asteroids.v1.levels` / `sw.asteroids.v1.levels.ver`
- [x] 关卡数据：`.levels.json` 120 关（levelNo 1..120 连续，name 唯一 ≤32 字，difficulty 1-10）

## 关卡数据格式

```json
{ "levelNo": 1, "name": "近轨巡航·第1关", "difficulty": 1, "data": { "name": "近轨巡航·第1关", "waves": 1, "seed": 104768, "lives": 3 } }
```

- `waves`：本关波次数（1-8），每波小行星数量 3+波次序号（上限 10）
- `seed`：确定性种子，引擎 `astSpawnWave(seed, w)` 复现该波初始布局（不与飞船出生点重叠、不出界、两两不重叠）
- `lives`：本关生命数（1-5，本作为前 60 关 3 命、后 60 关 2 命）
