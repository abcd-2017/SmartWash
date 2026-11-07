// 游戏规范审计（加固版 2026-11-07）：按 GAME-THEME.md §2/§3/§4/§5.1/§6 逐条机器核对
// 严格模式（新游戏）：全项硬性检查；宽松模式（存量）：功能等价检查 + 遗留偏离信息项
// 加固点：布局类型判定（消灭全屏误报）、存档键全量提取（消灭漏检/误报）、热区选择器扩展、
//         §4 裁撤残留检查、theme-color 同步检查、token 基线值比对、JS 颜色字面量检查、
//         setChrome 位置检查、canvas 字号灰区单列、素材前缀补 st-。
// 用法：node audit-gates.mjs [game…]（缺省审计全部游戏目录）
import { readFileSync, readdirSync, statSync } from "fs";
import { join } from "path";
import { fileURLToPath } from "url";

const DIR = fileURLToPath(new URL(".", import.meta.url));
const CANONICAL = readFileSync(join(DIR, "block-drop/index.html"), "utf8");

// ── 新游戏清单（出生即按现行 §6 规范实现）──
const STRICT = new Set([
  "tetris", "sudoku", "idiom", "pacman", "invaders", "lights-out",
  "sokoban", "connect-four", "solitaire", "fruit-slice", "air-hockey", "reversi",
  "magic-tower", "crossword"
]);

// §2 基础 token + §3 已登记素材 token 前缀
const BASE_TOKENS = new Set([
  "--g-bg", "--g-surface", "--g-surface2", "--g-primary", "--g-primary-deep", "--g-primary-soft", "--g-on-soft",
  "--g-text", "--g-muted", "--g-faint", "--g-border", "--g-hairline", "--g-danger", "--g-warning", "--g-success",
  "--g-water", "--g-scrim", "--g-shadow", "--g-radius", "--g-radius-btn", "--g-radius-inner", "--g-gap", "--g-on-primary"
]);
const MATERIAL_PREFIX = /^--g-(bp|bshade|field|grid|snake|food|sky|sky1|sky2|cloud|cld|gnd|gnl|pipe|pipecap|pipeln|bird1|bird2|birdln|wing|beak|bd|line|star|ball|ballln|maze|card|cell|cellhi|celllo|open|openln|n[1-8]|fs-|felt|felt2|rail|pocket|fr-|ss-|moon|moon2|board|tilesuper|st-|mt-)/;
const FONT_TIER = new Set([36, 28, 22, 18, 16, 14, 12]);
const SHELL_RADIUS = new Set([16, 14, 12, 10]);

// §2 基线值（浅/深；radius 系为常量）。用于 ⑯ token 基线值比对。
const BASELINE_LIGHT = {
  "--g-bg": "#f5f6f3", "--g-surface": "#ffffff", "--g-surface2": "#eff1ee", "--g-primary": "#2d9b6a",
  "--g-primary-deep": "#1e8c5c", "--g-primary-soft": "#e8f6ef", "--g-on-soft": "#0b3625",
  "--g-text": "#1c1c1e", "--g-muted": "#6b6e73", "--g-faint": "#a9aea9", "--g-border": "#e6e9e6",
  "--g-hairline": "#eff1ee", "--g-danger": "#c9526b", "--g-warning": "#d98a26", "--g-success": "#2d9b6a",
  "--g-water": "#4a9fd4", "--g-scrim": "rgba(245,246,243,0.9)",
  "--g-radius": "16px", "--g-radius-btn": "14px", "--g-radius-inner": "12px", "--g-gap": "16px", "--g-on-primary": "#ffffff"
};
const BASELINE_DARK = {
  "--g-bg": "#141614", "--g-surface": "#1d201e", "--g-surface2": "#262a27", "--g-primary": "#2d9b6a",
  "--g-primary-deep": "#5bc894", "--g-primary-soft": "#1d3229", "--g-on-soft": "#b9e8cf",
  "--g-text": "#e6e9e6", "--g-muted": "#a9aea9", "--g-faint": "#7e837d", "--g-border": "#2c312d",
  "--g-hairline": "#262a27", "--g-danger": "#db7186", "--g-warning": "#e2a44e", "--g-success": "#4ade6e",
  "--g-water": "#6bb4de", "--g-scrim": "rgba(20,22,20,0.9)"
};
const SHADOW_OK = [
  "02px12pxrgba(20,40,30,0.05)", "02px12pxrgba(0,0,0,0.28)",          // 卡片档
  "010px24pxrgba(20,40,30,0.1)", "010px24pxrgba(0,0,0,0.45)"           // 浮层档（gomoku §3 登记）
];

// canvas 字号例外（§3 灰区：素材字号受素材尺寸硬约束，逐值登记）
const CANVAS_FONT_EXEMPT = {
  billiards: [9, 17], "moon-lander": [10, 13], beat_tap: [11, 13, 15],
  "forest-run": [15], slingshot: [15], flappy: [38], breakout: [15]
};

function norm(s) { return s.replace(/\s+/g, ""); }
function themeRuntime(html) {
  const m = html.match(/<script>\s*(\/\/主题运行时[\s\S]*?)<\/script>/);
  return m ? m[1] : null;
}
// 严格比对前剥离 setChrome 行（全屏游戏按 §6#6 在运行时内追加，与范例差异仅此一行）
function stripSetChrome(rt) { return rt ? rt.replace(/\n?\s*try\{SmartWashGameBridge&&SmartWashGameBridge\.setChrome\([^)]*\)\}catch\(e\)\{\}/g, "") : rt; }
const canonRuntime = themeRuntime(CANONICAL);

// 颜色归一化：#abc→#aabbcc、#RRGGBBAA→rgba(...)、rgba 逗号归一、alpha 两位小数
function normColor(v) {
  v = String(v).trim().toLowerCase();
  let m;
  if ((m = v.match(/^#([0-9a-f]{3})$/))) return "#" + [...m[1]].map((c) => c + c).join("");
  if ((m = v.match(/^#([0-9a-f]{6})$/))) return "#" + m[1];
  if ((m = v.match(/^#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/))) {
    const a = Math.round(parseInt(m[4], 16) / 2.55) / 100;
    return `rgba(${parseInt(m[1], 16)},${parseInt(m[2], 16)},${parseInt(m[3], 16)},${a})`;
  }
  if ((m = v.match(/^rgba?\(([^)]+)\)$/))) {
    const p = m[1].split(",").map((s) => s.trim());
    if (p.length === 4) return `rgba(${p[0]},${p[1]},${p[2]},${Math.round(parseFloat(p[3]) * 100) / 100})`;
    return `rgba(${p.slice(0, 3).join(",")},1)`;
  }
  return v;
}
function normShadow(v) { return normColor(String(v).replace(/\s+/g, " ").trim()).replace(/\s+/g, ""); }

// 解析 :root 浅色 与 [data-theme=dark] 深色两块的 token 值
function themeBlocks(html) {
  const css = html.replace(/\/\*[\s\S]*?\*\//g, "");
  const out = { light: {}, dark: {} };
  for (const m of css.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
    const sel = m[1].trim(), body = m[2];
    if (!/^(?:html\s*,\s*body|:root|\[data-theme)/.test(sel)) continue;
    if (/data-theme\s*=\s*["']?dark/.test(sel)) {
      for (const d of body.matchAll(/(--g-[a-z0-9-]+)\s*:\s*([^;]+);/g)) out.dark[d[1]] = d[2].trim();
    } else if (/^:root\b/.test(sel)) {
      for (const d of body.matchAll(/(--g-[a-z0-9-]+)\s*:\s*([^;]+);/g)) out.light[d[1]] = d[2].trim();
    }
  }
  return out;
}

// 布局类型：card（有 .wrap）/ fullscreen（html,body height:100%）/ unknown
function layoutType(html) {
  if (/\.wrap\s*\{/.test(html)) return "card";
  if (/html\s*,\s*body\s*\{[^}]*height:\s*100%/.test(html)) return "fullscreen";
  return "unknown";
}

// 存档键全量提取：常量表解析 + 直接调用 + 封装函数调用点（去注释后扫描最后一段 script）
function extractStorageKeys(html) {
  const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map((m) => m[1]);
  const code = (scripts.length ? scripts[scripts.length - 1] : "")
    .replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/[^\n]*/g, "");
  const consts = {};
  for (const m of code.matchAll(/(?:var|let|const)\s+([^;]+?);/g)) {
    for (const d of m[1].split(",")) {
      const dm = d.match(/([A-Za-z_$][\w$]*)\s*=\s*(["'])((?:(?!\2).)*)\2/);
      if (dm) consts[dm[1]] = dm[3];
    }
  }
  const keys = new Set(), dynamic = new Set();
  const addArg = (arg) => {
    arg = arg.trim();
    const lit = arg.match(/^(["'])((?:(?!\1).)*)\1$/);
    if (lit) { keys.add(lit[2]); return; }
    if (consts[arg]) { keys.add(consts[arg]); return; }
    if (/^[A-Za-z_$][\w$]*$/.test(arg)) return; // 形参/标识符，非字面量
    dynamic.add(arg);
  };
  for (const m of code.matchAll(/localStorage\s*\.\s*(?:getItem|setItem|removeItem)\s*\(\s*([^;]+?)\s*(?:,|\))/g)) addArg(m[1]);
  // 封装函数：函数体引用 localStorage 且实参进入其调用 → 解析该函数的调用点
  //（函数体可能含 try/catch 嵌套花括号，故用签名后 400 字符窗口而非闭括号匹配）
  const wrappers = new Set();
  const ID_RE = /^[A-Za-z_$][\w$]*$/;
  const scanFn = (name, params, body) => {
    if (!/localStorage/.test(body) || !ID_RE.test(name)) return;
    for (const p of params) {
      if (!ID_RE.test(p)) continue;
      const re = new RegExp("localStorage\\s*\\.\\s*(?:getItem|setItem|removeItem)\\s*\\([^)]*\\b" + p + "\\b[^)]*\\)");
      if (re.test(body)) { wrappers.add(name); return; }
    }
  };
  for (const m of code.matchAll(/function\s+([A-Za-z_$][\w$]*)\s*\(([^)]*)\)/g)) scanFn(m[1], m[2].split(",").map((s) => s.trim()), code.slice(m.index + m[0].length, m.index + m[0].length + 400));
  for (const m of code.matchAll(/([A-Za-z_$][\w$]*)\s*:\s*function\s*\(([^)]*)\)/g)) scanFn(m[1], m[2].split(",").map((s) => s.trim()), code.slice(m.index + m[0].length, m.index + m[0].length + 400));
  // 对象方法简写 get(key){...}（排除 if/for/while/switch/catch 控制流）
  for (const m of code.matchAll(/(?!(?:if|for|while|switch|catch|function|return)\b)([A-Za-z_$][\w$]*)\s*\(([^)]*)\)\s*\{/g)) scanFn(m[1], m[2].split(",").map((s) => s.trim()), code.slice(m.index + m[0].length, m.index + m[0].length + 400));
  for (const name of wrappers) {
    for (const m of code.matchAll(new RegExp("\\b" + name + "\\s*\\(\\s*([^)]+)\\)", "g"))) {
      // 键是首参；调用可能带多参（read("key",0)），截取首参（引号串或标识符）
      const arg0 = m[1].match(/^\s*(["'][^"']*["']|[A-Za-z_$][\w$]*)/);
      if (arg0) addArg(arg0[1]);
    }
  }
  return { keys: [...keys], dynamic: [...dynamic] };
}

// JS 颜色字面量扫描（最后一个 script 块），按行豁免：行尾注释含例外/保留/恒白/恒暗/恒亮/素材
function scanJsColors(html) {
  const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map((m) => m[1]);
  const code = scripts.length ? scripts[scripts.length - 1] : "";
  const bad = [];
  for (const line of code.split("\n")) {
    if (!/(#[0-9a-fA-F]{3,8}\b|rgba?\()/.test(line)) continue;
    if (/(?:例外|保留|恒白|恒暗|恒亮|素材)/.test(line)) continue;
    const probe = line.replace(/\/\/.*$/, "");
    if (/rgba?\(\s*255\s*,\s*255\s*,\s*255/.test(probe)) continue; // 恒白
    if (/rgba?\(\s*0\s*,\s*0\s*,\s*0/.test(probe)) continue;       // 恒黑阴影
    if (/#[fF]{3}\b|#[fF]{6}\b/.test(probe)) continue;             // 恒白 hex
    if (/rgba?\(\s*["']?\s*\+/.test(probe)) continue;              // 动态构造（非字面色）
    bad.push(line.trim().slice(0, 90));
  }
  return bad;
}

function audit(name) {
  const html = readFileSync(join(DIR, name, "index.html"), "utf8");
  const strict = STRICT.has(name);
  const fail = [];   // 违规（新游戏必须清零）
  const notes = [];  // 信息项（存量遗留偏离 / 严格模式的提示）
  const info = {};
  const P = (msg) => (strict ? fail : notes).push(msg);

  // ① 不自绘标题（全部硬性）
  const h1 = (html.match(/<h1[\s>]/g) || []).length;
  info.h1 = h1;
  if (h1 > 0) fail.push(`存在 ${h1} 个 <h1>`);

  // ② 主题运行时（新游戏归一化对齐范例；存量功能等价即可）
  const rt = themeRuntime(html);
  const functional = /__applySmartWashTheme=function\(m\)\{if\(m==="light"\|\|m==="dark"\)/.test(html) &&
    /getTheme/.test(html) && /dataset\.theme/.test(html);
  if (strict) {
    if (!rt) fail.push("主题运行时缺失");
    else if (norm(stripSetChrome(rt)) !== norm(stripSetChrome(canonRuntime))) fail.push("主题运行时与 block-drop 范例不一致");
    info.runtime = rt ? "一致" : "缺失";
  } else {
    if (!functional) fail.push("主题运行时功能要素缺失（getTheme/apply/theme 数据集）");
    else info.runtime = rt ? "变体(功能等价)" : "无注释变体(功能等价)";
  }

  // ③ meta theme-color 存在 + 浅色默认值 + paint() 同步（全部硬性）
  const metaM = html.match(/<meta\s+name="theme-color"\s+content="([^"]*)"/);
  if (!metaM) fail.push("缺 meta[name=theme-color]");
  else if (normColor(metaM[1]) !== normColor("#F5F6F3")) P(`theme-color 初值非浅色 bg: ${metaM[1]}`);
  if (metaM && !/meta\[name=["']?theme-color["']?\]/.test(html)) P("paint() 未同步 meta[name=theme-color]");

  // ④ setChrome 与布局类型匹配（§5.1：卡片式 0 处；全屏画布须 immersive 且置于 head 运行时内）
  const chrome = (html.match(/setChrome/g) || []).length;
  const layout = layoutType(html);
  info.setChrome = chrome; info.layout = layout;
  if (layout === "card" && chrome > 0) fail.push(`卡片式布局出现 ${chrome} 处 setChrome`);
  if (layout === "fullscreen") {
    if (chrome === 0) P("全屏画布未调 setChrome('immersive')");
    else if (rt && !/setChrome/.test(rt)) P("setChrome 未置于 head 主题运行时内");
    else if (rt && !/setChrome\(['"]immersive/.test(rt)) P("setChrome 非 immersive");
  }

  // ⑤ CSS 字号八档（新游戏硬性；存量信息项）
  const fontSizes = new Set();
  for (const m of html.matchAll(/font-size:\s*(\d+(?:\.\d+)?)px/g)) fontSizes.add(+m[1]);
  for (const m of html.matchAll(/font:\s*(?:inherit|\d+\s+)(\d+(?:\.\d+)?)px/g)) fontSizes.add(+m[1]);
  const badFonts = [...fontSizes].filter((s) => !FONT_TIER.has(s));
  info.fonts = [...fontSizes].sort((a, b) => a - b).join("/");
  if (badFonts.length) P(`字号越档: ${badFonts.join(",")}`);

  // ⑥ 圆角（UI 壳 ∈16/14/12/10；>10 越档新游戏硬性；小圆角/999 为素材或胶囊例外）
  const radii = new Set();
  for (const m of html.matchAll(/border-radius:\s*([^;}]+)/g)) {
    for (const v of m[1].matchAll(/(\d+(?:\.\d+)?)px/g)) radii.add(+v[1]);
    if (/%/.test(m[1])) radii.add("%");
  }
  info.radii = [...radii].join("/");
  const badShell = [...radii].filter((r) => r !== "%" && !SHELL_RADIUS.has(r) && r > 10 && r !== 999);
  if (badShell.length) P(`UI 壳疑似越档圆角: ${badShell.join(",")}`);

  // ⑦ token（未定义引用全部硬性；自造 token 新游戏硬性）
  const defined = new Set();
  for (const m of html.matchAll(/(--g-[a-z0-9-]*[a-z0-9])\s*:/g)) defined.add(m[1]);
  const used = new Set();
  for (const m of html.matchAll(/var\((--g-[a-z0-9-]*[a-z0-9])/g)) used.add(m[1]);
  const undef = [...used].filter((t) => !defined.has(t));
  const dynamicTok = undef.filter((t) => [...defined].some((d) => d.startsWith(t + "-")));
  const trulyUndef = undef.filter((t) => !dynamicTok.includes(t));
  info.tokens = used.size;
  if (trulyUndef.length) fail.push(`使用了未定义的 token: ${trulyUndef.join(",")}`);
  if (dynamicTok.length) notes.push(`动态拼接 token（运行时解析）: ${dynamicTok.join(",")}`);
  const nonBase = [...defined].filter((t) => !BASE_TOKENS.has(t) && !MATERIAL_PREFIX.test(t));
  if (nonBase.length) P(`疑似自造 token（不在 §2/§3）: ${nonBase.join(",")}`);

  // ⑧ 桥钩子 + canvas 调色板（全部硬性）
  if (!/__applySmartWashTheme/.test(html)) fail.push("缺 __applySmartWashTheme");
  if (/getContext\("2d"\)/.test(html)) {
    info.canvas = true;
    if (!/palLoad/.test(html)) fail.push("canvas 游戏缺 palLoad 调色板刷新");
    if (!/window\.__gOnTheme\s*=\s*(?:function\(\)\s*\{\s*palLoad|\(\)\s*=>\s*\{\s*palLoad)/.test(html))
      fail.push("__gOnTheme 未接 palLoad");
  } else info.canvas = false;

  // ⑨ body 触屏五件套（新游戏硬性；存量缺项信息）
  const bodyCss = [...html.matchAll(/(?:^|\})\s*body\s*\{([^}]*)\}/gm)].map((m) => m[1]).join("\n");
  const missing = ["user-select", "-webkit-touch-callout", "-webkit-tap-highlight-color", "touch-action", "overscroll-behavior"]
    .filter((r) => !bodyCss.includes(r));
  if (missing.length) P(`body 缺触屏规则: ${missing.join(",")}`);

  // ⑩ 布局（按类型判定：卡片查 480/padding；全屏查 height:100% + overflow:hidden）
  if (layout === "card") {
    if (!/max-width:\s*480px/.test(html)) P("缺 .wrap max-width:480px");
    if (!/padding:\s*16px 8px 30px/.test(html)) P("body padding 非 16px 8px 30px");
  } else if (layout === "fullscreen") {
    const hb = [...html.matchAll(/(?:^|\})\s*(?:html\s*,\s*body|body)\s*\{([^}]*)\}/gm)].map((m) => m[1]).join(";");
    if (!/height:\s*100%/.test(hb)) P("全屏缺 html,body height:100%");
    if (!/overflow:\s*hidden/.test(hb)) P("全屏缺 overflow:hidden（flex 形态豁免见 §5.1 待裁定）");
  } else P("布局类型未知（无 .wrap 且无全屏特征）");

  // ⑪ 热区（交互类选择器 ≥44px；compound 感知：.btn.warn 取 .btn；排除玩法素材 .card/.cell）
  const INTERACTIVE_PART = /(?:^|\.)(btn|ctl|key|lamp|lvbtn|pad|song|pbtn|icon-btn|ctrl-btn|lv|lv-chip)(?:$|[:.\[])/i;
  const small = [];
  for (const m of html.matchAll(/([^{}]+)\{([^}]*)\}/g)) {
    const sel = m[1].replace(/\/\*[\s\S]*?\*\//g, "").trim(), body = m[2];
    const last = sel.split(/\s+/).pop().toLowerCase();
    const isBtn = last === "button" || INTERACTIVE_PART.test(last);
    if (!isBtn || /\.card\b|\.cell\b/.test(last)) continue;
    const mh = body.match(/min-height:\s*(\d+(?:\.\d+)?)px/);
    if (mh && +mh[1] < 44) small.push(sel + ":" + mh[1]);
    const h = body.match(/(?<!min-)height:\s*(\d+(?:\.\d+)?)px/);
    if (h && !mh && +h[1] < 44) small.push(sel + ":" + h[1]);
  }
  info.hotzone = small.length ? small.join(" ") : "按钮全 ≥44";
  if (small.length) fail.push(`按钮热区 <44px: ${small.join(" ")}`);

  // ⑫ 存档键（全量提取：新游戏全部键须 sw.<key>.v1；存量为 §6 豁免信息项）
  const extracted = extractStorageKeys(html);
  const swKey = html.match(/sw\.([a-z0-9-]+)\.v1/);
  const badKeys = extracted.keys.filter((k) => !/^sw\.[a-z0-9-]+\.v1$/.test(k));
  info.saveKey = extracted.keys.length
    ? (extracted.keys.length === 1 ? extracted.keys[0] : `${extracted.keys.length}键`) + (badKeys.length ? `（${badKeys.length} 遗留）` : "")
    : "无存档";
  if (extracted.keys.length === 0 && !swKey) {
    if (strict) fail.push("未找到存档键");
  } else if (strict) {
    if (!swKey) fail.push("存档键不符 sw.<key>.v1");
    if (badKeys.length) fail.push(`存档键不符规范: ${badKeys.join(", ")}`);
  } else if (badKeys.length) {
    notes.push(`遗留存档键: ${badKeys.join(" ")}`);
  }
  if (extracted.dynamic.length) notes.push(`动态拼键: ${extracted.dynamic.join(" ")}`);

  // ⑬ 零外链（全部硬性）
  if (/https?:\/\//.test(html)) fail.push("存在 http(s) 外链");

  // ⑭ §4 裁撤残留（matchMedia/prefers-color-scheme/g-theme/切换按钮；全部硬性）
  const residue = [];
  if (/matchMedia\(/.test(html)) residue.push("matchMedia");
  if (/prefers-color-scheme/.test(html)) residue.push("prefers-color-scheme");
  if (/["']g-theme["']/.test(html)) residue.push("g-theme localStorage");
  if (/__gThemeToggle|g-theme-toggle|btnTheme/.test(html)) residue.push("切换按钮");
  if (residue.length) fail.push(`§4 裁撤残留: ${residue.join(",")}`);

  // ⑮ token 基线值比对（§2 已定义 token 的值须与基线一致；素材 token 不比对）
  const blocks = themeBlocks(html);
  const drift = [];
  for (const [mode, table] of [["light", BASELINE_LIGHT], ["dark", BASELINE_DARK]]) {
    for (const [tok, base] of Object.entries(table)) {
      const val = blocks[mode][tok];
      if (val === undefined) continue; // 未定义不在此查（深色块只写与浅色不同的 token）
      if (tok === "--g-shadow") {
        if (!SHADOW_OK.includes(normShadow(val))) drift.push(`${mode} ${tok}=${val}`);
      } else if (normColor(val) !== normColor(base)) {
        drift.push(`${mode} ${tok}=${val}`);
      }
    }
  }
  if (drift.length) P(`token 基线偏离: ${drift.slice(0, 6).join("; ")}${drift.length > 6 ? " 等" + drift.length + "项" : ""}`);

  // ⑯ canvas 字号灰区（ctx.font 单列：越档且未登记例外的记信息项）
  if (info.canvas) {
    const cvFonts = new Set();
    for (const m of html.matchAll(/\.font\s*=\s*([^;\n]+)/g)) {
      const rhs = m[1];
      for (const pm of rhs.matchAll(/(\d+(?:\.\d+)?)px/g)) cvFonts.add(+pm[1]);
      if (rhs.includes("+") || rhs.includes("?")) {
        // 动态拼接/三元：抓独立两位整数（10–59，天然排除 400/600/800 字重）
        for (const pm of rhs.matchAll(/\b(\d{2})\b/g)) {
          const n = +pm[1];
          if (n >= 10 && n <= 59) cvFonts.add(n);
        }
      }
    }
    const exempt = CANVAS_FONT_EXEMPT[name.replace(/-/g, "_")] || CANVAS_FONT_EXEMPT[name] || [];
    const badCv = [...cvFonts].filter((s) => !FONT_TIER.has(s) && !exempt.includes(s));
    info.canvasFonts = [...cvFonts].sort((a, b) => a - b).join("/") || "-";
    if (badCv.length) notes.push(`canvas 字号越档: ${badCv.join(",")}`);
  }

  // ⑰ JS 颜色字面量（§3：零字面量，恒白/恒暗/行尾例外注释豁免；新游戏硬性）
  const badColors = scanJsColors(html);
  if (badColors.length) P(`JS 颜色字面量: ${badColors.length} 处（首处: ${badColors[0]}）`);

  return { name, strict, info, fail, notes };
}

const targets = process.argv.slice(2);
const games = targets.length ? targets : readdirSync(DIR).filter((d) =>
  !d.startsWith(".") && !d.endsWith(".md") && statSync(join(DIR, d)).isDirectory());

let bad = 0, strictBad = 0;
for (const g of games.sort()) {
  const r = audit(g);
  if (r.fail.length) { bad++; if (r.strict) strictBad++; }
  console.log(`${r.fail.length ? "❌" : "✅"} ${g.padEnd(14)} [${r.strict ? "严格" : "存量"}] 运行时:${r.info.runtime} 布局:${r.info.layout || "?"} h1:${r.info.h1} setChrome:${r.info.setChrome} 字号:${r.info.fonts || "-"}${r.info.canvasFonts ? "(cv:" + r.info.canvasFonts + ")" : ""} 圆角:${r.info.radii} 热区:${r.info.hotzone} 存档:${r.info.saveKey}`);
  r.fail.forEach((p) => console.log("     ❌ " + p));
  r.notes.forEach((p) => console.log("     · " + p));
}
console.log(`\n审计 ${games.length} 款：严格模式 ${strictBad} 款违规；存量模式另有 ${bad - strictBad} 款违规（遗留项见信息行）`);
process.exit(bad ? 1 : 0);
