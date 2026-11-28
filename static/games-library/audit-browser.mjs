#!/usr/bin/env node
// 游戏浏览器级审计（运行时维度，与 audit-gates / audit-ux-gates 互补）
// 静态脚本查不出来的东西：白屏、JS 运行时报错、点击被遮挡、元素零尺寸、
// 横向溢出、canvas 尺寸为 0、字号过小、禁用缩放、字符字形冒充图形等。
//
// 用法：
//   node audit-browser.mjs                 # 全量 168 款
//   node audit-browser.mjs snake g2048     # 指定游戏
//   node audit-browser.mjs --bridge        # 注入 SmartWashGameBridge 假桥，跑通存档/关卡链路
//   node audit-browser.mjs --shots         # 额外输出截图（默认输出到 /tmp/sw-browser-audit/shots）
//
// 输出：browser-audit.json（结构化）+ 终端分组摘要。退出码 1 = 有 P0 级发现。

import { createRequire } from "module";
import { existsSync, readFileSync, readdirSync, mkdirSync, writeFileSync } from "fs";
import { join, extname } from "path";
import { fileURLToPath } from "url";
import { createServer } from "http";
import { readFile } from "fs/promises";

const require = createRequire(import.meta.url);
let chromium;
for (const p of ["playwright", "/opt/homebrew/lib/node_modules/playwright"]) {
  try { ({ chromium } = require(p)); break; } catch {}
}
if (!chromium) {
  console.error("找不到 playwright。请先安装：npm i -g playwright");
  process.exit(2);
}

const DIR = fileURLToPath(new URL(".", import.meta.url));
const OUT = "/tmp/sw-browser-audit";
const SHOTS = join(OUT, "shots");
mkdirSync(SHOTS, { recursive: true });

const args = process.argv.slice(2);
const WITH_BRIDGE = args.includes("--bridge");
const WITH_SHOTS = args.includes("--shots") || true; // 默认出图，便于目检
const only = args.filter((a) => !a.startsWith("--"));

// ---------- 静态服务器（比 file:// 更接近真机加载，避免 file:// 的路径怪癖） ----------
const MIME = { ".html": "text/html; charset=utf-8", ".js": "text/javascript", ".css": "text/css", ".png": "image/png", ".svg": "image/svg+xml", ".json": "application/json" };
function startServer() {
  const server = createServer(async (req, res) => {
    try {
      const url = decodeURIComponent(req.url.split("?")[0]);
      const file = join(DIR, url === "/" ? "README.md" : url);
      const body = await readFile(file);
      res.writeHead(200, { "content-type": MIME[extname(file)] || "application/octet-stream" });
      res.end(body);
    } catch {
      res.writeHead(404); res.end("nf");
    }
  });
  return new Promise((r) => server.listen(0, "127.0.0.1", () => r(server)));
}

// ---------- 探针：在页面里跑，返回运行时可观测事实 ----------
function probe() {
  const all = (sel) => Array.from(document.querySelectorAll(sel));
  const tagOf = (el) => {
    if (!el) return "?";
    const cls = (el.className && typeof el.className === "string") ? el.className.trim().split(/\s+/).slice(0, 2).join(".") : "";
    return el.tagName.toLowerCase() + (el.id ? "#" + el.id : "") + (cls ? "." + cls : "");
  };
  const vis = (el) => {
    const s = getComputedStyle(el);
    const r = el.getBoundingClientRect();
    return s.display !== "none" && s.visibility !== "hidden" && +s.opacity > 0.01 && r.width > 0 && r.height > 0;
  };
  const rectOf = (el) => {
    const r = el.getBoundingClientRect();
    return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) };
  };
  const interactiveSel = 'button,[role="button"],a[href],input,select,textarea,summary,[onclick],[data-action],[data-move],.btn,.ctl,.lvbtn,.cell,.tile,.piece,.card';
  const interactive = all(interactiveSel).filter(vis);

  const targets = [];
  for (const el of interactive) {
    const r = el.getBoundingClientRect();
    const s = getComputedStyle(el);
    const rec = {
      el: tagOf(el),
      r: rectOf(el),
      small: r.width < 24 || r.height < 24 ? "P0" : r.width < 44 || r.height < 44 ? "P1" : null,
      cursor: s.cursor,
      peNone: s.pointerEvents === "none",
      peInherited: (() => { let a = el.parentElement; while (a && a !== document.body) { if (getComputedStyle(a).pointerEvents === "none") return true; a = a.parentElement; } return false; })(),
      zero: r.width === 0 || r.height === 0,
      disabled: !!el.disabled,
      blockedBy: null,
      aria: el.getAttribute("aria-label") || el.getAttribute("title") || (el.textContent || "").trim().slice(0, 12),
    };
    if (!rec.zero && !rec.peNone) {
      const cx = r.x + r.width / 2, cy = r.y + r.height / 2;
      const hit = document.elementFromPoint(cx, cy);
      // 同一父容器内的重叠是牌堆/层叠布局的设计（如纸牌扇形叠放），不算被遮挡
      const peer = hit && hit !== el && hit.parentElement && hit.parentElement === el.parentElement;
      if (hit && hit !== el && !el.contains(hit) && !hit.contains(el) && !peer) rec.blockedBy = tagOf(hit);
    }
    targets.push(rec);
  }

  // 字符字形冒充图形：叶子节点、文本是符号字形、且兄弟节点众多（棋盘/牌堆特征）
  const GLYPH = /[♔-♟♠-♧♛⚀-⚅■-◿←-⇿⬀-⯿✓✗❤⭐☀-⛿\u{1F000}-\u{1F0FF}]/u;
  const glyphs = [];
  for (const el of all("body *")) {
    if (el.children.length) continue;
    const txt = (el.textContent || "").trim();
    if (!txt || txt.length > 2 || !GLYPH.test(txt)) continue;
    const s = getComputedStyle(el);
    if (s.display === "none") continue;
    const siblings = el.parentElement ? el.parentElement.children.length : 0;
    if (siblings < 3) continue;
    glyphs.push({ el: tagOf(el), txt, size: parseFloat(s.fontSize), siblings, color: s.color });
  }

  const canvases = all("canvas").map((c) => {
    const r = c.getBoundingClientRect();
    const s = getComputedStyle(c);
    return { id: c.id || null, aria: c.getAttribute("aria-label"), role: c.getAttribute("role"), w: Math.round(r.width), h: Math.round(r.height), cssW: s.width, cssH: s.height, hidden: s.display === "none" || +s.opacity < 0.01 };
  });

  const de = document.documentElement;
  const vw = window.innerWidth;
  const overflowX = Math.max(0, de.scrollWidth - de.clientWidth);

  // 视口 / 缩放
  const vp = document.querySelector('meta[name="viewport"]');
  const vpContent = vp ? vp.getAttribute("content") : null;
  const zoomDisabled = /user-scalable\s*=\s*no|maximum-scale\s*=\s*1/.test(vpContent || "");

  // 字号过小
  const smallText = [];
  for (const el of all("body *")) {
    if (el.children.length) continue;
    if (!(el.textContent || "").trim()) continue;
    const s = getComputedStyle(el);
    if (s.display === "none" || s.visibility === "hidden") continue;
    const fs = parseFloat(s.fontSize);
    if (fs && fs < 12) smallText.push({ el: tagOf(el), fs, txt: (el.textContent || "").trim().slice(0, 16) });
  }

  // 固定层遮挡
  const fixedCover = [];
  for (const el of all("body *")) {
    const s = getComputedStyle(el);
    if (s.position !== "fixed") continue;
    const r = el.getBoundingClientRect();
    if (r.width * r.height > vw * window.innerHeight * 0.6) fixedCover.push({ el: tagOf(el), r: rectOf(el), opacity: s.opacity, display: s.display });
  }

  // 可见遮罩（.overlay / .sheet / [role=dialog]）——启动遮罩是设计，关不掉才是 bug
  const overlays = all(".overlay,.sheet,[role='dialog'],[role='dialog']").filter((o) => {
    const s = getComputedStyle(o);
    const r = o.getBoundingClientRect();
    return s.display !== "none" && s.visibility !== "hidden" && +s.opacity > 0.01 && r.width > 0 && r.height > 0;
  }).map((o) => ({
    el: tagOf(o),
    r: rectOf(o),
    buttons: Array.from(o.querySelectorAll("button,[role='button'],.btn,[onclick],[data-action]")).map((b) => (b.textContent || "").trim().slice(0, 10)).filter(Boolean),
  }));

  // 主题运行时（GAME-THEME §4/§5：无手动切换按钮，深色只能由桥推送 __applySmartWashTheme）
  const hasThemeRuntime = typeof window.__applySmartWashTheme === "function";

  return {
    title: document.title,
    bodyTextLen: (document.body.innerText || "").trim().length,
    canvasCount: all("canvas").length,
    canvases,
    overflowX,
    docW: de.scrollWidth,
    vw,
    vp: vpContent,
    zoomDisabled,
    interactiveCount: interactive.length,
    targets,
    glyphs,
    smallText,
    fixedCover,
    hasThemeRuntime,
    theme: document.documentElement.dataset.theme || "light",
    darkRules: /\[data-theme\s*=\s*["']?dark["']?\]/.test(document.documentElement.outerHTML),
    overlays,
  };
}

function tagOf(el) {
  if (!el) return "?";
  const cls = (el.className && typeof el.className === "string") ? el.className.trim().split(/\s+/).slice(0, 2).join(".") : "";
  return el.tagName.toLowerCase() + (el.id ? "#" + el.id : "") + (cls ? "." + cls : "");
}

// ---------- 假桥：跑通存档 / 关卡 / 成绩链路 ----------
const BRIDGE_INIT = `
window.SmartWashGameBridge = {
  loadState: function(key, cb){ setTimeout(function(){ var f=window.__gOnGameBridgeResult; if(f) f(cb, true, {stateJson: JSON.stringify({lv: 3, wins: 2})}); }, 4); },
  saveState: function(key, payload, cb){ setTimeout(function(){ var f=window.__gOnGameBridgeResult; if(f) f(cb, true, {}); }, 4); },
  reportScore: function(key, payload, cb){ setTimeout(function(){ var f=window.__gOnGameBridgeResult; if(f) f(cb, true, {}); }, 4); },
  getLevels: function(key, cb){ setTimeout(function(){ var f=window.__gOnGameBridgeResult; if(f) f(cb, true, {version: 1, levels: []}); }, 4); }
};
`;

const DISMISS_SRC = "开始|启动|进入|继续|确认|确定|ok|start|play|go|重开|再来|关闭|返回|开始游戏|继续游戏|下一关";
// 关掉启动遮罩（开始界面是设计；关不掉才是 bug）。最多 3 轮，避免死循环。
async function dismissOverlays(page) {
  // 逐个试按钮，直到遮罩真的关掉（最多 5 轮）。
  // 选曲菜单要点歌曲、对话要点确认——没有万能规则，所以「点了看结果」最稳。
  for (let round = 0; round < 5; round++) {
    const act = await page.evaluate(() => {
      const vis = Array.from(document.querySelectorAll(".overlay,.sheet,[role='dialog']")).filter((o) => {
        const s = getComputedStyle(o), r = o.getBoundingClientRect();
        return s.display !== "none" && s.visibility !== "hidden" && +s.opacity > 0.01 && r.width > 0 && r.height > 0;
      });
      if (!vis.length) return "none";
      const o = vis[0];
      const btns = Array.from(o.querySelectorAll("button,[role='button'],.btn,[onclick],[data-action]"));
      if (!btns.length) return "nobtn";
      // 优先主按钮，否则第一个（开始菜单/选曲菜单的第一个就是可点的推进项；对话框点掉也行）
      const pick = btns.find((b) => /primary|main|start|confirm/i.test(b.className || "")) || btns[0];
      try { pick.click(); } catch {}
      const stillVisible = getComputedStyle(o).display !== "none" && +getComputedStyle(o).opacity > 0.01;
      return stillVisible ? "again" : "ok";
    }).catch(() => "none");
    if (act === "none" || act === "ok" || act === "nobtn") break;
    await page.waitForTimeout(350);
  }
  await page.waitForTimeout(250);
}

async function main() {
  const dirs = only.length
    ? only
    : readdirSync(DIR).filter((d) => existsSync(join(DIR, d, "index.html")));

  const server = await startServer();
  const port = server.address().port;
  const base = `http://127.0.0.1:${port}`;

  const browser = await chromium.launch({ channel: "chrome", headless: true });
  const results = [];
  const t0 = Date.now();

  for (let i = 0; i < dirs.length; i++) {
    const name = dirs[i];
    const ctx = await browser.newContext({
      viewport: { width: 390, height: 844 },
      deviceScaleFactor: 1,
      isMobile: true,
      hasTouch: true,
      locale: "zh-CN",
      colorScheme: "light",
    });
    const page = await ctx.newPage();
    if (WITH_BRIDGE) await page.addInitScript(BRIDGE_INIT);

    const errors = [];
    page.on("pageerror", (e) => errors.push("PAGEERROR " + e.message));
    page.on("console", (m) => {
      if (m.type() !== "error") return;
      const u = (m.location() && m.location().url) || "";
      if (/favicon\.ico/.test(u) || /favicon\.ico/.test(m.text())) return;   // 浏览器自动请求，环境噪音
      errors.push("CONSOLE " + m.text() + " @" + u);
    });
    page.on("requestfailed", (r) => errors.push("REQFAIL " + r.url() + " " + (r.failure()?.errorText || "")));
    // 404 只记非 favicon 的（浏览器自动请求 favicon 属环境噪音，全库 168 款都没有 favicon）
    page.on("response", (r) => { if (r.status() >= 400 && !/favicon\.ico/.test(r.url())) errors.push("HTTP" + r.status() + " " + r.url()); });

    const rec = { game: name, errors: [], probe: null, shots: [], status: "ok" };
    try {
      await page.goto(`${base}/${name}/index.html`, { waitUntil: "load", timeout: 15000 });
      await page.waitForTimeout(900);
      const loadProbe = await page.evaluate(probe);
      rec.loadOverlays = loadProbe.overlays;
      rec.loadBodyTextLen = loadProbe.bodyTextLen;
      rec.loadCanvasCount = loadProbe.canvasCount;
      await dismissOverlays(page);
      rec.probe = await page.evaluate(probe);
      if (WITH_SHOTS) {
        const light = join(SHOTS, `${name}-light.jpg`);
        await page.screenshot({ path: light, type: "jpeg", quality: 72 });
        rec.shots.push(light);
      }
      // 深色主题截图：走桥推送（GAME-THEME §4 唯一入口），等 canvas 重绘
      if (rec.probe?.hasThemeRuntime) {
        try {
          await page.evaluate(() => window.__applySmartWashTheme && window.__applySmartWashTheme("dark"));
          await page.waitForTimeout(700);
          rec.darkTheme = await page.evaluate(() => document.documentElement.dataset.theme);
          if (WITH_SHOTS) {
            const dark = join(SHOTS, `${name}-dark.jpg`);
            await page.screenshot({ path: dark, type: "jpeg", quality: 72 });
            rec.shots.push(dark);
          }
        } catch {}
      }
      // 真实点击抽查：主 CTA（开始 / 重来 / 确认），Playwright 的 actionability 检查能识别被遮挡
      const cta = rec.probe?.targets?.find((t) => /开始|重来|重开|继续|确认|启动|play|start|restart|ok/i.test(t.aria)) || rec.probe?.targets?.find((t) => t.el.includes("#btn"));
      if (cta) {
        try {
          await page.mouse.click(cta.r.x + cta.r.w / 2, cta.r.y + cta.r.h / 2, { timeout: 1500 });
          await page.waitForTimeout(250);
        } catch (e) {
          rec.errors.push("CLICK_FAIL " + cta.el + " " + String(e.message).split("\n")[0]);
        }
      }
    } catch (e) {
      rec.status = "LOAD_FAIL";
      rec.errors.push("LOAD_FAIL " + String(e.message).split("\n")[0]);
    }
    rec.errors = [...new Set(rec.errors.concat(errors))];
    results.push(rec);
    await ctx.close();
    if ((i + 1) % 20 === 0) console.log(`  … ${i + 1}/${dirs.length}（${((Date.now() - t0) / 1000).toFixed(0)}s）`);
  }

  await browser.close();
  server.close();

  writeFileSync(join(OUT, "browser-audit.json"), JSON.stringify(results, null, 1));

  // ---------- 分组摘要 ----------
  const p0 = [], p1 = [], p2 = [];
  for (const r of results) {
    const p = r.probe;
    if (r.status === "LOAD_FAIL") { p0.push(`✗ ${r.game}：加载失败 ${r.errors[0] || ""}`); continue; }
    if (!p) { p0.push(`✗ ${r.game}：探针无返回`); continue; }
    if (r.errors.length) p0.push(`✗ ${r.game}：运行时报错 ${r.errors.length} 条 → ${r.errors.slice(0, 3).join(" | ")}`);
    if ((r.loadBodyTextLen || 0) < 20 && (r.loadCanvasCount || 0) === 0) p0.push(`✗ ${r.game}：疑似白屏（正文 ${r.loadBodyTextLen} 字、无 canvas）`);
    // 启动即弹出胜负结算 = 存档状态泄漏，进游戏前就该是干净局面
    for (const o of r.loadOverlays || []) {
      const txt = (o.buttons || []).join(" ");
      if (/胜利|过关|通关|结束|失败|游戏 over|game over|win|lose/i.test(txt) || o.buttons.length === 0)
        p0.push(`✗ ${r.game}：进游戏即弹遮罩 ${o.el}（${txt || "无按钮"}）`);
    }
    // 关闭遮罩后仍存在的遮罩 = 关不掉，真「点不到」
    for (const o of p.overlays || []) p0.push(`✗ ${r.game}：遮罩 ${o.el} 关不掉，仍挡住界面（按钮：${(o.buttons || []).join("/") || "无"}）`);
    for (const c of p.canvases) if (c.hidden || c.w === 0 || c.h === 0) p0.push(`✗ ${r.game}：canvas${c.id ? "#" + c.id : ""} 尺寸为 0 或隐藏（${c.w}×${c.h}）`);
    for (const t of p.targets) {
      if (t.zero) p0.push(`✗ ${r.game}：零尺寸可点元素 ${t.el}`);
      else if (t.peNone && !t.peInherited && (t.cursor === "pointer" || /^button/i.test(t.el) || t.el.includes("[role"))) p2.push(`· ${r.game}：${t.el} pointer-events:none 但看起来可点`);
      if (t.blockedBy) p0.push(`✗ ${r.game}：${t.el}「${t.aria}」被 ${t.blockedBy} 遮挡，点不到`);
      if (t.small === "P0") p1.push(`△ ${r.game}：${t.el}「${t.aria}」热区 ${t.r.w}×${t.r.h} < 24`);
      else if (t.small === "P1") p2.push(`· ${r.game}：${t.el}「${t.aria}」热区 ${t.r.w}×${t.r.h} < 44`);
    }
    if (p.overflowX > 2) p1.push(`△ ${r.game}：横向溢出 ${p.overflowX}px（doc ${p.docW} / 视口 ${p.vw}）`);
    if (p.zoomDisabled) p1.push(`△ ${r.game}：viewport 禁用了用户缩放`);
    for (const s of p.smallText.slice(0, 3)) p2.push(`· ${r.game}：字号 ${s.fs}px ${s.el}「${s.txt}」`);
    for (const f of p.fixedCover) p1.push(`△ ${r.game}：固定层 ${f.el} 覆盖 ${Math.round((f.r.w * f.r.h) / (p.vw * 844) * 100)}% 视口`);
    if (p.glyphs.length >= 3) p2.push(`· ${r.game}：${p.glyphs.length} 处字符字形充当图形（例：${p.glyphs.slice(0, 4).map((g) => g.txt).join(" ")}）`);
  }

  const line = (t, a) => { if (a.length) { console.log(`\n${t}（${a.length}）`); console.log(a.slice(0, 80).join("\n")); if (a.length > 80) console.log(`  … 其余 ${a.length - 80} 条见 JSON`); } };
  console.log(`\n浏览器级审计 ${results.length} 款，耗时 ${((Date.now() - t0) / 1000).toFixed(0)}s，桥：${WITH_BRIDGE ? "注入" : "无"}`);
  line("P0 · 阻断（报错 / 白屏 / 点不到 / 零尺寸）", p0);
  line("P1 · 显示异常（溢出 / 热区过小 / 固定层遮挡 / 禁缩放）", p1);
  line("P2 · 体验与视觉（字形冒充图形 / 小字号 / pointer-events）", p2);
  console.log(`\n明细：${OUT}/browser-audit.json${WITH_SHOTS ? `　截图：${SHOTS}` : ""}`);
  process.exit(p0.length ? 1 : 0);
}

main().catch((e) => { console.error(e); process.exit(2); });
