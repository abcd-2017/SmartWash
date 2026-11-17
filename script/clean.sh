#!/usr/bin/env bash

set -Eeuo pipefail

# ============================================================
# SmartWash 本机工作残留清理
#
# 用法：
#   bash script/clean.sh
#
# agent 会话会在本机留下无价值残留，本脚本一键清理：
#   1. .DS_Store（Finder 缓存）
#   2. 旧 zcode 工作流草稿/运行脚本（仅删 7 天前的）
#   3. 空目录骨架与 hvigor .test 脚手架（git 不跟踪空目录，删空树不丢代码）
#   4. 后端运行日志与 ~/.zcode 粘贴附件缓存（仅删 7 天前的）
#
# 自带防护：含文件的目录一律跳过（脚本不做非空删除，仅第 3 步
# .test 为 gitignore 约定的生成物例外）；绝不触碰 build/、
# node_modules/、oh_modules/、.gradle/ 等构建产物与依赖目录，
# 其体积在末尾报告，需要时手动清理。
# ============================================================

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEEP_DAYS=7
cd "$ROOT"

# --- 1. .DS_Store ---
n=$(find . -name ".DS_Store" \
    -not -path "./.git/*" -not -path "*/node_modules/*" \
    -not -path "*/build/*" -not -path "*/oh_modules/*" \
    -delete -print | wc -l | tr -d ' ')
echo "1/4 .DS_Store：删除 ${n} 个"

# --- 2. 旧工作流草稿/运行脚本 ---
n=0
for d in .zcode/workflow-drafts .zcode/workflow-runs \
         SmartWash-Android/.zcode/workflow-drafts SmartWash-Android/.zcode/workflow-runs; do
  [ -d "$d" ] || continue
  c=$(find "$d" -type f -mtime +"$KEEP_DAYS" -delete -print | wc -l | tr -d ' ')
  n=$((n + c))
done
echo "2/4 旧工作流草稿/运行脚本（>${KEEP_DAYS} 天）：删除 ${n} 个"

# --- 3. 空目录骨架与 hvigor .test ---
find SmartWash_Harmony -type d -name ".test" -prune -exec rm -rf {} + 2>/dev/null || true
n=$(find . -type d -empty \
    -not -path "./.git/*" -not -path "./.zcode/*" \
    -not -path "*/node_modules/*" -not -path "*/oh_modules/*" \
    -not -path "*/build/*" -not -path "*/.gradle/*" \
    -not -path "*/.hvigor/*" -not -path "*/.idea/*" \
    -delete -print | wc -l | tr -d ' ')
echo "3/4 空目录骨架 + hvigor .test：删除 ${n} 个"

# --- 4. 日志与粘贴附件缓存 ---
rm -rf SmartWash/logs
n=0
if [ -d "$HOME/.zcode/tmp/paste-attachments" ]; then
  n=$(find "$HOME/.zcode/tmp/paste-attachments" -maxdepth 1 -mindepth 1 \
      -type d -mtime +"$KEEP_DAYS" -exec rm -rf {} + -print 2>/dev/null | wc -l | tr -d ' ')
fi
echo "4/4 运行日志与粘贴附件缓存（>${KEEP_DAYS} 天）：清理 ${n} 个日期目录"

# --- 报告：可再生构建产物（本脚本不触碰，需要时手动清） ---
echo "---- 可再生构建产物（未清理） ----"
du -sh SmartWash-Android/.gradle \
       SmartWash_Harmony/entry/build \
       SmartWash_Harmony/.hvigor \
       SmartWash/target \
       SmartWashWeb/dist 2>/dev/null || true
builds=$(find SmartWash-Android -maxdepth 3 -type d -name build -prune 2>/dev/null || true)
if [ -n "$builds" ]; then
  total=$(echo "$builds" | tr '\n' '\0' | xargs -0 du -sk 2>/dev/null \
          | awk '{s+=$1} END {print int(s/1024)}')
  echo "SmartWash-Android 全部 build/ 目录合计 ${total} MB"
fi
nm=$(du -sh SmartWashWeb/node_modules 2>/dev/null | cut -f1 || true)
[ -n "$nm" ] && echo "SmartWashWeb/node_modules ${nm}（重装即可恢复）"
echo "清理完成。"
