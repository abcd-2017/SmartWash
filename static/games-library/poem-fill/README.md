# 诗词填空（gameKey：`poem-fill`，category 建议：`word`）

- 玩法：关卡制古诗词填空。填句关给出一句诗（缺 1-3 字），从含形近/同音干扰的候选字中选正确的字填入；接句关给出上句，从候选句中选出相连的下一句。限时完成、点错扣机会，按剩余时间与失误评定 1-3 星，通关解锁下一关。
- 操作：触屏点选。填句先点高亮空缺（可切换），再点候选字按钮；点对自动锁定并跳到下一处，点错该字置灰并扣一次机会。接句直接点候选句。可随时「重玩本关」，过关后点「下一关」。
- 说明：单文件自包含 H5（CSS/JS 全内联、零外链、纯 DOM 自绘，无素材文件）。支持管理后台下发关卡（`GB.getLevels("poem-fill")`）与包内默认关卡兜底；进度双写本地与账号，成绩经 `reportScore` 上报。题库取自公有领域经典古诗词，诗意提示为自撰。

## 关卡数据格式

顶层 JSON 数组，每项信封 `{"levelNo":N,"name":"…","difficulty":D,"data":{…}}`，`levelNo` 1..N 连续。两种题型：

- `fill`：`{name,type:"fill",poem:"《静夜思》·李白",hint:"自撰提示",lines:["床前明月光"],blanks:[{line:0,pos:2,answer:"明",options:["明","名","鸣"]}],time,mistakes}`
- `link`：`{name,type:"link",poem:"《静夜思》·李白",hint:"自撰提示",prompt:"床前明月光",answer:"疑是地上霜",options:[...],time,mistakes}`

约束：`name` ≤32 字；`difficulty` 1-10；`time` 10-300 秒；`mistakes` 1-5；`blanks` 1-3 处且同句位置不重复；`options` 3-6 项、无重复、答案恰出现一次、单字选项（fill）或整句（link）；`answer` 必须与 `lines[line][pos]` 一致。任一条非法则整批拒绝（回退包内默认）。

## 星级与成绩

星级 = 1 +（剩余时间比例 ≥35%）+（全程零失误），封顶 3 星；本关得分 = 100 + 剩余秒数×2 + 剩余机会×30。上报 `metricValue=已过关卡数`、`metricExtra=累计总分`（desc，越大越好）。

## 自查清单

- [x] 无外部请求：CSS/JS 全内联，零 CDN / 字体 / 图片（grep 零命中）
- [x] `node audit-gates.mjs poem-fill` 严格模式 0 违规
- [x] `node theme-gates.mjs poem-fill` 通过（语法/外链/体积）
- [x] `.levels.json` 110 关通过独立校验器（契约/结构/答案唯一性/题库一致性/主干句去重/数量/字节）
- [x] `parseLevels` 解析单测含非法批次整批放弃
- [x] 无头 Chrome e2e：真实点选通关 1 关 + 非法 DB 批次拒绝 + 主题推送 + 375px 无横向溢出
