#!/usr/bin/env node
// 三关检查（GAME-THEME.md §7）：① <script> 语法 ② 零外链 ③ 体积增幅
// 用法：node theme-gates.mjs <gameDir>…  （ gameDir 内须有 index.html ）
import { readFileSync, statSync } from "node:fs";
import { join } from "node:path";

const EXTERNAL = /https?:\/\/|\/\/[a-z0-9.-]+\.(com|cn|net|org)\b|\bsrc=["']\/\//i;
let failed = false;

for (const dir of process.argv.slice(2)) {
  const file = join(dir, "index.html");
  const html = readFileSync(file, "utf8");
  const name = dir.split("/").pop();
  const problems = [];

  // ① 提取全部内联 <script> 逐段 new Function 解析（只解析不执行）
  const scripts = [...html.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi)];
  if (scripts.length === 0) problems.push("未找到内联 <script>");
  for (let i = 0; i < scripts.length; i++) {
    try { new Function(scripts[i][1]); }
    catch (e) { problems.push(`script#${i + 1} 语法错误: ${e.message}`); }
  }

  // ② 外链（注释掉的也算——零外链是合集立身之本）
  const lines = html.split("\n");
  lines.forEach((l, i) => { if (EXTERNAL.test(l)) problems.push(`L${i + 1} 疑似外链: ${l.trim().slice(0, 80)}`); });

  // ③ 体积：改后 ≤ 改前×1.3（改前从 git 取，无 git 记录则只报当前值）
  const now = statSync(file).size;
  let base = null;
  try {
    const { execSync } = await import("node:child_process");
    base = parseInt(execSync(`git show HEAD:${JSON.stringify(file)} 2>/dev/null | wc -c`, { shell: "/bin/bash" }).toString(), 10) || null;
  } catch { /* 非 git 或新文件 */ }
  let sizeMsg = `size ${now}B`;
  if (base) {
    const pct = ((now - base) / base * 100).toFixed(1);
    sizeMsg += ` (was ${base}B, +${pct}%)`;
    if (now > base * 1.3) problems.push(`体积增幅 ${pct}% 超 30%`);
  }

  const ok = problems.length === 0;
  if (!ok) failed = true;
  console.log(`${ok ? "✅" : "❌"} ${name}  ${sizeMsg}${problems.length ? "\n   - " + problems.join("\n   - ") : ""}`);
}
process.exit(failed ? 1 : 0);
