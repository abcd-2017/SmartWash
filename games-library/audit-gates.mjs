// 游戏规范审计：按 GAME-THEME.md §2/§4/§5.1/§6 逐条机器核对
// 严格模式（新游戏，11 款）：全项硬性检查；宽松模式（存量 17 款）：功能等价检查 + 遗留偏离信息项
// 用法：node audit-gates.mjs [game…]（缺省审计全部游戏目录）
import { readFileSync, readdirSync, statSync } from "fs";
import { join } from "path";
import { fileURLToPath } from "url";

const DIR = fileURLToPath(new URL(".", import.meta.url));
const CANONICAL = readFileSync(join(DIR, "block-drop/index.html"), "utf8");

// ── 新游戏清单（两批共 11 款，出生即按现行 §6 规范实现）──
const STRICT = new Set([
  "tetris", "sudoku", "idiom", "pacman", "invaders", "lights-out",
  "sokoban", "connect-four", "solitaire", "fruit-slice", "air-hockey"
]);

// §2 基础 token + §3 已记录的各游戏素材 token 前缀
const BASE_TOKENS = new Set([
  "--g-bg", "--g-surface", "--g-surface2", "--g-primary", "--g-primary-deep", "--g-primary-soft", "--g-on-soft",
  "--g-text", "--g-muted", "--g-faint", "--g-border", "--g-hairline", "--g-danger", "--g-warning", "--g-success",
  "--g-water", "--g-scrim", "--g-shadow", "--g-radius", "--g-radius-btn", "--g-radius-inner", "--g-gap", "--g-on-primary"
]);
const MATERIAL_PREFIX = /^--g-(bp|bshade|field|grid|snake|food|sky|sky1|sky2|cloud|cld|gnd|gnl|pipe|pipecap|pipeln|bird1|bird2|birdln|wing|beak|bd|line|star|ball|ballln|maze|card|cell|cellhi|celllo|open|openln|n[1-8]|fs-|felt|felt2|rail|pocket|fr-|ss-|moon|moon2|board|tilesuper)/;
const FONT_TIER = new Set([36, 28, 22, 18, 16, 14, 12]);
const SHELL_RADIUS = new Set([16, 14, 12, 10]);

function norm(s) { return s.replace(/\s+/g, ""); }
function themeRuntime(html) {
  const m = html.match(/<script>\s*(\/\/主题运行时[\s\S]*?)<\/script>/);
  return m ? m[1] : null;
}
const canonRuntime = themeRuntime(CANONICAL);

function audit(name) {
  const html = readFileSync(join(DIR, name, "index.html"), "utf8");
  const strict = STRICT.has(name);
  const fail = [];   // 违规（新游戏必须清零）
  const notes = [];  // 信息项（存量遗留偏离 / 严格模式的提示）
  const info = {};

  // ① 不自绘标题（全部硬性）
  const h1 = (html.match(/<h1[\s>]/g) || []).length;
  info.h1 = h1;
  if (h1 > 0) fail.push(`存在 ${h1} 个 <h1>`);

  // ② 主题运行时（新游戏逐字节对齐范例；存量功能等价即可）
  const rt = themeRuntime(html);
  const functional = /__applySmartWashTheme=function\(m\)\{if\(m==="light"\|\|m==="dark"\)/.test(html) &&
    /getTheme/.test(html) && /dataset\.theme/.test(html);
  if (strict) {
    if (!rt) fail.push("主题运行时缺失");
    else if (norm(rt) !== norm(canonRuntime)) fail.push("主题运行时与 block-drop 范例不一致");
    info.runtime = rt ? "逐字节一致" : "缺失";
  } else {
    if (!functional) fail.push("主题运行时功能要素缺失（getTheme/apply/theme 数据集）");
    else info.runtime = rt ? "变体(功能等价)" : "无注释变体(功能等价)";
  }

  // ③ meta theme-color（全部硬性）
  if (!/<meta\s+name="theme-color"/.test(html)) fail.push("缺 meta[name=theme-color]");

  // ④ setChrome 与布局类型匹配（§5.1：卡片式 0 处；全屏画布 immersive）
  const chrome = (html.match(/setChrome/g) || []).length;
  const isCard = /\.wrap\s*\{/.test(html);
  info.setChrome = chrome;
  if (isCard && chrome > 0) fail.push(`卡片式布局出现 ${chrome} 处 setChrome`);
  if (!isCard && chrome === 0 && strict) fail.push("全屏画布游戏未调 setChrome('immersive')");

  // ⑤ 字号八档（新游戏硬性；存量信息项，玩法素材小字属 §3 例外）
  const fontSizes = new Set();
  for (const m of html.matchAll(/font-size:\s*(\d+(?:\.\d+)?)px/g)) fontSizes.add(+m[1]);
  for (const m of html.matchAll(/font:\s*(?:inherit|\d+\s+)(\d+(?:\.\d+)?)px/g)) fontSizes.add(+m[1]);
  for (const m of html.matchAll(/font="[^"]*?(\d+(?:\.\d+)?)px/g)) fontSizes.add(+m[1]);
  const badFonts = [...fontSizes].filter((s) => !FONT_TIER.has(s));
  info.fonts = [...fontSizes].sort((a, b) => a - b).join("/");
  if (badFonts.length) (strict ? fail : notes).push(`字号越档: ${badFonts.join(",")}`);

  // ⑥ 圆角（UI 壳 ∈16/14/12/10；>10 越档新游戏硬性，存量信息项；小圆角/999 为素材或胶囊例外）
  const radii = new Set();
  for (const m of html.matchAll(/border-radius:\s*([^;}]+)/g)) {
    for (const v of m[1].matchAll(/(\d+(?:\.\d+)?)px/g)) radii.add(+v[1]);
    if (/%/.test(m[1])) radii.add("%");
  }
  info.radii = [...radii].join("/");
  const badShell = [...radii].filter((r) => r !== "%" && !SHELL_RADIUS.has(r) && r > 10 && r !== 999);
  if (badShell.length) (strict ? fail : notes).push(`UI 壳疑似越档圆角: ${badShell.join(",")}`);

  // ⑦ token（未定义引用全部硬性；自造 token 新游戏硬性）
  const defined = new Set();
  for (const m of html.matchAll(/(--g-[a-z0-9-]*[a-z0-9])\s*:/g)) defined.add(m[1]);
  const used = new Set();
  for (const m of html.matchAll(/var\((--g-[a-z0-9-]*[a-z0-9])/g)) used.add(m[1]);
  const undef = [...used].filter((t) => !defined.has(t));
  const dynamic = undef.filter((t) => [...defined].some((d) => d.startsWith(t + "-")));
  const trulyUndef = undef.filter((t) => !dynamic.includes(t));
  info.tokens = used.size;
  if (trulyUndef.length) fail.push(`使用了未定义的 token: ${trulyUndef.join(",")}`);
  if (dynamic.length) notes.push(`动态拼接 token（运行时解析）: ${dynamic.join(",")}`);
  const nonBase = [...defined].filter((t) => !BASE_TOKENS.has(t) && !MATERIAL_PREFIX.test(t));
  if (nonBase.length && strict) fail.push(`疑似自造 token（不在 §2/§3）: ${nonBase.join(",")}`);
  if (nonBase.length && !strict) notes.push(`素材 token: ${nonBase.length} 个（§3 命名约定内）`);

  // ⑧ 桥钩子 + canvas 调色板（全部硬性）
  if (!/__applySmartWashTheme/.test(html)) fail.push("缺 __applySmartWashTheme");
  if (/getContext\("2d"\)/.test(html)) {
    info.canvas = true;
    if (!/palLoad/.test(html)) fail.push("canvas 游戏缺 palLoad 调色板刷新");
    if (!/window\.__gOnTheme\s*=\s*(?:function\(\)\s*\{\s*palLoad|\(\)\s*=>\s*\{\s*palLoad)/.test(html))
      fail.push("__gOnTheme 未接 palLoad");
  } else info.canvas = false;

  // ⑨ body 触屏规则（新游戏硬性；存量缺项信息）
  const bodyCss = [...html.matchAll(/(?:^|})\s*body\s*\{([^}]*)\}/g)].map((m) => m[1]).join("\n");
  const missing = ["user-select", "-webkit-touch-callout", "-webkit-tap-highlight-color", "touch-action", "overscroll-behavior"]
    .filter((r) => !bodyCss.includes(r));
  if (missing.length) (strict ? fail : notes).push(`body 缺触屏规则: ${missing.join(",")}`);

  // ⑩ 布局（新游戏硬性；存量信息项——遗留 420/460 等宽度为历史版本）
  if (!/max-width:\s*480px/.test(html)) (strict ? fail : notes).push("缺 .wrap max-width:480px");
  if (!/padding:\s*16px 8px 30px/.test(html)) (strict ? fail : notes).push("body padding 非 16px 8px 30px");

  // ⑪ 热区（按钮类 ≥44px，全部硬性；仅判定选择器最后一层 compound，排除按钮内视觉子元素）
  const INTERACTIVE = /^(?:button|\.btn|\.ctl|\.key|\.chip|\.lamp|\.lvbtn)$/i;
  const small = [];
  for (const m of html.matchAll(/([^{}]+)\{([^}]*)\}/g)) {
    const sel = m[1].trim(), body = m[2];
    const last = sel.split(/\s+/).pop();
    if (!INTERACTIVE.test(last)) continue;
    const mh = body.match(/min-height:\s*(\d+(?:\.\d+)?)px/);
    if (mh && +mh[1] < 44) small.push(sel + ":" + mh[1]);
    const h = body.match(/(?<!min-)height:\s*(\d+(?:\.\d+)?)px/);
    if (h && !mh && +h[1] < 44) small.push(sel + ":" + h[1]);
  }
  info.hotzone = small.length ? small.join(" ") : "按钮全 ≥44";
  if (small.length) fail.push(`按钮热区 <44px: ${small.join(" ")}`);

  // ⑫ 存档键（新游戏须 sw.<key>.v1；存量为 §6「存档键不改」遗留键，信息项）
  const swKey = html.match(/sw\.([a-z0-9-]+)\.v1/);
  const anyKey = html.match(/(?:localStorage\.(?:getItem|setItem)\(\s*["']([^"']+)["']|["']([a-z][a-z0-9_.-]*best[a-z0-9_.-]*)["'])/);
  if (strict) {
    info.saveKey = swKey ? "sw." + swKey[1] + ".v1" : "未找到";
    if (!swKey) fail.push("存档键不符 sw.<key>.v1");
  } else {
    info.saveKey = swKey ? "sw." + swKey[1] + ".v1" : (anyKey ? (anyKey[1] || anyKey[2]) : "无存档");
    if (!swKey && anyKey) notes.push(`遗留存档键: ${anyKey[1] || anyKey[2]}`);
  }

  // ⑬ 零外链（全部硬性）
  if (/https?:\/\//.test(html)) fail.push("存在 http(s) 外链");

  return { name, strict, info, fail, notes };
}

const targets = process.argv.slice(2);
const games = targets.length ? targets : readdirSync(DIR).filter((d) =>
  !d.startsWith(".") && !d.endsWith(".md") && statSync(join(DIR, d)).isDirectory());

let bad = 0, strictBad = 0;
for (const g of games.sort()) {
  const r = audit(g);
  if (r.fail.length) { bad++; if (r.strict) strictBad++; }
  console.log(`${r.fail.length ? "❌" : "✅"} ${g.padEnd(14)} [${r.strict ? "严格" : "存量"}] 运行时:${r.info.runtime} h1:${r.info.h1} setChrome:${r.info.setChrome} 字号:${r.info.fonts} 圆角:${r.info.radii} 热区:${r.info.hotzone} 存档:${r.info.saveKey}`);
  r.fail.forEach((p) => console.log("     ❌ " + p));
  r.notes.forEach((p) => console.log("     · " + p));
}
console.log(`\n审计 ${games.length} 款：严格模式 ${strictBad} 款违规；存量模式另有 ${bad - strictBad} 款违规（遗留项见信息行）`);
process.exit(bad ? 1 : 0);
