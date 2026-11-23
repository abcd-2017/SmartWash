#!/usr/bin/env node
// 游戏 UX 增量审计（ui-ux-pro-max + impeccable audit 维度，2026-11-22）
// 与 audit-gates.mjs 互补：本脚本只查 skill 规则的可机器判定项，不重复项目规范。
// 用法：node audit-ux-gates.mjs [gameDir…]（缺省全部含 index.html 的目录）
import { existsSync, readFileSync, readdirSync } from "fs";
import { join } from "path";
import { fileURLToPath } from "url";

const DIR = fileURLToPath(new URL(".", import.meta.url));

// ---------- 颜色工具 ----------
function hex2rgb(h) {
  h = h.trim().replace("#", "");
  if (h.length === 3) h = h.split("").map((c) => c + c).join("");
  const n = parseInt(h, 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}
function lum([r, g, b]) {
  const f = (c) => { c /= 255; return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); };
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
}
function ratio(fg, bg) {
  const [f, b] = [lum(fg), lum(bg)].sort((x, y) => y - x);
  return (f + 0.05) / (b + 0.05);
}
function parseThemeBlock(css, selectorRe) {
  const m = css.match(selectorRe);
  if (!m) return null;
  const vars = {};
  for (const v of m[1].matchAll(/(--g-[a-z0-9-]+)\s*:\s*([^;]+);/g)) vars[v[1]] = v[2].trim();
  return vars;
}
function rgbaOn(bg, fg, alpha) {
  const b = hex2rgb(bg), f = hex2rgb(fg);
  return b.map((c, i) => Math.round(c * (1 - alpha) + f[i] * alpha));
}

// ---------- 主流程 ----------
const dirs = process.argv.slice(2);
const targets = dirs.length ? dirs : readdirSync(DIR).filter((d) => existsSync(join(DIR, d, "index.html")));
let fail = 0, warn = 0;
const report = [];

for (const name of targets) {
  const file = join(DIR, name, "index.html");
  const html = readFileSync(file, "utf8");
  const issues = [], infos = [];
  const css = html.match(/<style>([\s\S]*?)<\/style>/i)?.[1] || "";

  // ① token 对比度（light + dark 两套 :root）
  const light = parseThemeBlock(css, /:root\s*\{([^}]*)\}/) || {};
  const dark = parseThemeBlock(css, /(?::root)?\[data-theme=["']?dark["']?\]\s*\{([^}]*)\}/) || {};
  const pairs = [
    ["--g-text", ["--g-bg", "--g-surface", "--g-surface2"], 4.5],
    ["--g-muted", ["--g-bg", "--g-surface", "--g-surface2"], 4.5],
    ["--g-primary-deep", ["--g-surface", "--g-bg"], 4.5],
    ["--g-on-soft", ["--g-primary-soft"], 4.5],
    ["#fff", ["--g-btn-primary"], 4.5],
  ];
  for (const [theme, vars] of [["light", light], ["dark", dark]]) {
    if (!Object.keys(vars).length) { issues.push(`${theme} 主题 :root 变量缺失`); continue; }
    for (const [fgK, bgKs, min] of pairs) {
      const fg = vars[fgK] || (fgK === "#fff" ? "#ffffff" : null);
      if (!fg || !/^#[0-9a-f]{3,8}$/i.test(fg)) continue;
      for (const bgK of bgKs) {
        const bg = vars[bgK];
        if (!bg || !/^#[0-9a-f]{3,8}$/i.test(bg)) continue;
        const r = ratio(hex2rgb(fg), hex2rgb(bg));
        if (r < min) issues.push(`${theme}: ${fgK} on ${bgK} 对比度 ${r.toFixed(2)} < ${min}`);
      }
    }
  }

  // ② reduced-motion
  if (!/prefers-reduced-motion/i.test(html)) issues.push("缺 prefers-reduced-motion 适配");

  // ③ 焦点态（有 button 却无 focus 样式）
  if (/<button/i.test(html) && !/:focus(-visible)?\b/i.test(css)) issues.push("按钮无 :focus/:focus-visible 样式");

  // ④ canvas 无障碍
  const canvases = html.match(/<canvas[^>]*>/gi) || [];
  canvases.forEach((c, i) => { if (!/aria-label|role=/i.test(c)) issues.push(`canvas#${i + 1} 无 aria-label/role`); });

  // ⑤ faint 不得承载文本（GAME-THEME.md §2 明文：faint 对 bg 仅 2.1:1，提示/页脚一律 --g-muted）
  const faintText = [...html.matchAll(/color:\s*var\(--g-faint\)/g)].length;
  if (faintText) issues.push(`color: var(--g-faint) 承载文本 ${faintText} 处（应改 --g-muted）`);

  // ⑥ emoji 当图标（按钮/可点元素内含 emoji 且无文字）——宽松：只报按钮内纯 emoji
  for (const m of html.matchAll(/<button[^>]*>([\s\S]*?)<\/button>/gi)) {
    if (/aria-label|aria-labelledby|title=/i.test(m[0])) continue;
    const inner = m[1].replace(/<[^>]+>/g, "").trim();
    if (inner && /^\p{Extended_Pictographic}+$/u.test(inner)) issues.push(`按钮纯 emoji 无文字/aria-label: "${inner}"`);
  }

  // ⑥ 横向滚动风险
  if (/overflow-x\s*:\s*hidden\s*;\s*[^}]*min-width\s*:\s*(4[5-9]\d|[5-9]\d\d|\d{4,})px/i.test(css)) issues.push("min-width>440px 且 overflow-x hidden");
  const wideFixed = [...css.matchAll(/width\s*:\s*(4[5-9]\d|[5-9]\d\d|\d{4,})px/g)].length;
  if (wideFixed > 3) infos.push(`固定 width>440px 出现 ${wideFixed} 次`);

  // ⑦ 性能：will-change / backdrop-filter
  const wc = (html.match(/will-change/g) || []).length;
  if (wc > 4) infos.push(`will-change ${wc} 处`);
  const bf = (html.match(/backdrop-filter/g) || []).length;
  if (bf > 2) infos.push(`backdrop-filter ${bf} 处`);

  // ⑧ touch-action（body 级 manipulation 已有则跳过）
  if (!/touch-action\s*:\s*manipulation/i.test(css)) infos.push("body 无 touch-action: manipulation");

  const status = issues.length ? "❌" : "✅";
  if (issues.length) fail++; else warn += infos.length;
  report.push(`${status} ${name}${issues.length ? "\n   ✗ " + issues.join("\n   ✗ ") : ""}${infos.length ? "\n   · " + infos.join("\n   · ") : ""}`);
}
console.log(report.join("\n"));
console.log(`\nUX 增量审计 ${targets.length} 款：违规 ${fail}，信息项 ${warn}`);
process.exit(fail ? 1 : 0);
