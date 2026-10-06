#!/usr/bin/env bash
# UI 规范静态门禁（设计规范落地第二批）——防 M3 交互组件回潮
#
# 规范依据（.claude/ui-design-spec.md）：
#   §7   交互反馈禁令（2026-10-06 定稿）：交互反馈不使用 Material ripple / state layer；
#        Material 交互组件（Button / TextButton / TextField 等自带 state layer 的）
#        禁止直接用于业务层可点击场景——一律 pressable / 自绘替代；既有裸 clickable
#        必须显式 indication = null 并配套 pressScale/pressAlpha。
#   §3.7 输入控件定稿：16sp 正文 / 尾缀一律图标化 / 验证码描边按钮。
#
# 实现策略（方向决策）：与 Material 默认行为的搏斗只允许发生在 common/ui 封装层；
# 业务层禁止直接使用 material3 交互组件。
#
# 检查范围：业务层 = app/src + feature/*/impl/src。
#   common/ui 封装层与 feature/divination（观象台）不在业务层扫描范围内。
# 挂账豁免（范围外批次收编，到期后从 EXEMPT 移除，逐条列注释）：
#   - feature/laundry/impl/.../laundry/LaundryPage.kt    —— TextButton×1 + LocalIndication×1（范围外挂账，见 PROGRESS.md）
#   - feature/payment/impl/.../payment/PaySuccessPage.kt —— 范围外挂账批次（cardRadius 等同批处理）
#   - feature/divination（观象台子系统）—— 整体范围外挂账（24 处间距 + LocalIndication×3 同批）

set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VIOLATIONS=0
check() {
  local desc="$1"; local count="$2"
  if [ "$count" != "0" ]; then
    echo "❌ $desc ($count 处)"
    VIOLATIONS=$((VIOLATIONS + 1))
  else
    echo "✅ $desc"
  fi
}

# 业务层扫描范围（业务层收编目标；观象台 feature/divination 与 common/ui 封装层不在内）
SCOPES=(app/src feature/user/impl/src feature/order/impl/src feature/payment/impl/src feature/laundry/impl/src feature/coupon/impl/src)

# 挂账豁免文件（grep 结果过滤，逐条注释见文件头）
EXEMPT_RE='feature/laundry/impl/src/.*LaundryPage\.kt|feature/payment/impl/src/.*PaySuccessPage\.kt'

echo "=== SmartWash-Android UI 规范门禁 ==="
echo ""

COLLECTED=0
collect() {
  # 汇总违规行：文件:行:内容，套用豁免
  for f in $(grep -rln --include='*.kt' -E "$1" "${SCOPES[@]}" 2>/dev/null); do
    grep -nE "$1" "$f" | sed "s|^|$f:|"
  done
}

# --- 检查 1a: M3 交互按钮直接调用（自带 state layer，§7 禁止）---
# Button( 需词边界避免误伤 AppButton(/IconButton(；文件含 material3 import 才判 M3
BUTTON_HITS=""
for f in $(grep -rln --include='*.kt' -E '(^|[^A-Za-z])Button\(' "${SCOPES[@]}" 2>/dev/null); do
  grep -q 'import androidx.compose.material3' "$f" || continue
  grep -nE '(^|[^A-Za-z])Button\(' "$f" | sed "s|^|$f:|" >> /tmp/check_ui_btn_$$
done
[ -f /tmp/check_ui_btn_$$ ] && BUTTON_HITS=$(cat /tmp/check_ui_btn_$$) && rm -f /tmp/check_ui_btn_$$
BTN_COUNT=0
if [ -n "$BUTTON_HITS" ]; then
  while IFS= read -r line; do
    case "$line" in
      *LaundryPage.kt*|*PaySuccessPage.kt*) continue ;;  # 挂账豁免（见文件头）
      *) echo "   ✗ $line"; BTN_COUNT=$((BTN_COUNT + 1)) ;;
    esac
  done <<< "$BUTTON_HITS"
fi
check "检查1a: material3 交互按钮直接调用（Button/TextButton/OutlinedButton）" "$BTN_COUNT"

# --- 检查 1b: ripple 体系（LocalIndication / rememberRipple）---
RIPPLE_COUNT=0
RIPPLE_HITS=$(collect 'LocalIndication|rememberRipple')
if [ -n "$RIPPLE_HITS" ]; then
  while IFS= read -r line; do
    case "$line" in
      *LaundryPage.kt*|*PaySuccessPage.kt*) continue ;;  # 挂账豁免（见文件头）
      *) echo "   ✗ $line"; RIPPLE_COUNT=$((RIPPLE_COUNT + 1)) ;;
    esac
  done <<< "$RIPPLE_HITS"
fi
check "检查1b: ripple 体系引用（LocalIndication / rememberRipple）" "$RIPPLE_COUNT"

# --- 检查 2: indication = LocalIndication.current 全仓禁止（common/ui 封装层除外）---
# 范围扩展到全仓 feature/（含观象台），观象台 3 处挂账豁免
LOCAL_IND_COUNT=0
LOCAL_IND_HITS=$(grep -rn --include='*.kt' 'indication = LocalIndication\.current' feature common 2>/dev/null | grep -v 'common/ui/')
if [ -n "$LOCAL_IND_HITS" ]; then
  while IFS= read -r line; do
    case "$line" in
      *feature/divination/*) continue ;;  # 观象台挂账豁免（见文件头）
      *LaundryPage.kt*|*PaySuccessPage.kt*) continue ;;  # 挂账豁免（见文件头）
      *) echo "   ✗ $line"; LOCAL_IND_COUNT=$((LOCAL_IND_COUNT + 1)) ;;
    esac
  done <<< "$LOCAL_IND_HITS"
fi
check "检查2: indication = LocalIndication.current（全仓，common/ui 除外）" "$LOCAL_IND_COUNT"

echo ""
if [ "$VIOLATIONS" -eq 0 ]; then
  echo "🎉 UI 规范门禁通过（M3 交互组件业务层清零）"
  echo "   违规修复指引：.claude/ui-design-spec.md §7（交互反馈禁令）/ §3.7（输入控件）"
  echo "   —— M3 按钮 → AppButton 三态或自绘 pressable；ripple → pressScale/pressAlpha"
  exit 0
else
  echo "⚠️  $VIOLATIONS 项违规"
  echo "   修复指引：.claude/ui-design-spec.md §7（交互反馈禁令）/ §3.7（输入控件）"
  echo "   —— M3 按钮 → AppButton 三态或自绘 pressable；ripple → pressScale/pressAlpha"
  exit 1
fi
