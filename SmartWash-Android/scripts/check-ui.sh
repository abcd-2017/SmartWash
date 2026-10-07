#!/usr/bin/env bash
# UI 规范静态门禁（设计规范落地第二批 + 第三批补齐 §3.7）——防 M3 交互组件回潮
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
#   common/ui 封装层与 feature/divination（观象台）不在业务层扫描范围内；
#   检查 3/4 属 §3.7 输入控件防回潮，针对封装层自身文件（PasswordInput/PhoneNumberInput）
#   与验证码行所在 RegisterPage 单独列出，不受业务层范围限制。
# 挂账豁免（范围外批次收编，到期后移除，逐条列注释）：
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
SCOPES=(app/src feature/user/impl/src feature/order/impl/src feature/payment/impl/src feature/laundry/impl/src feature/coupon/impl/src feature/update/src)

# §3.7 输入控件检查文件（common/ui 封装层自身 + 验证码行所在 RegisterPage）
INPUT_FILES=(
  common/ui/src/main/java/com/smartwash/common/ui/components/PasswordInput.kt
  common/ui/src/main/java/com/smartwash/common/ui/components/PhoneNumberInput.kt
  feature/user/impl/src/main/java/com/smartwash/feature/user/impl/ui/register/RegisterPage.kt
)

# M3 按钮调用正则（检查 1a 两处使用，改一处须同步另一处）：
# 五种 M3 按钮全量检测；前置 (^|[^A-Za-z]) 词边界避免误伤 AppButton( 等自封装组件
M3_BUTTON_RE='(^|[^A-Za-z])(Button|TextButton|OutlinedButton|FilledTonalButton|ElevatedButton)\('

# 汇总违规行：文件:行号:内容
collect() {
  for f in $(grep -rln --include='*.kt' -E "$1" "${SCOPES[@]}" 2>/dev/null); do
    grep -nE "$1" "$f" | sed "s|^|$f:|"
  done
}

# 临时文件（异常退出不留 /tmp 残留）
BTN_TMP="$(mktemp "${TMPDIR:-/tmp}/check_ui_btn.XXXXXX")" || exit 1
trap 'rm -f "$BTN_TMP"' EXIT

echo "=== SmartWash-Android UI 规范门禁 ==="
echo ""

# --- 检查 1a: M3 交互按钮直接调用（自带 state layer，§7 禁止）---
# 文件含 material3 import 才判 M3 调用
BUTTON_HITS=""
for f in $(grep -rln --include='*.kt' -E "$M3_BUTTON_RE" "${SCOPES[@]}" 2>/dev/null); do
  grep -q 'import androidx.compose.material3' "$f" || continue
  grep -nE "$M3_BUTTON_RE" "$f" | sed "s|^|$f:|" >> "$BTN_TMP"
done
[ -s "$BTN_TMP" ] && BUTTON_HITS=$(cat "$BTN_TMP")
BTN_COUNT=0
if [ -n "$BUTTON_HITS" ]; then
  while IFS= read -r line; do
    case "$line" in
      *LaundryPage.kt*|*PaySuccessPage.kt*) continue ;;  # 挂账豁免（见文件头）
      *) echo "   ✗ $line"; BTN_COUNT=$((BTN_COUNT + 1)) ;;
    esac
  done <<< "$BUTTON_HITS"
fi
check "检查1a: material3 交互按钮直接调用（Button/TextButton/OutlinedButton/FilledTonalButton/ElevatedButton）" "$BTN_COUNT"

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

# --- 检查 3: §3.7 输入正文 16sp（输入组件 14sp 回潮即违规；错误态 13sp 为规范允许，不在检查内）---
INPUT_14SP_HITS=""
for f in "${INPUT_FILES[@]}"; do
  [ -f "$f" ] || continue
  while IFS= read -r l; do
    [ -n "$l" ] && INPUT_14SP_HITS="${INPUT_14SP_HITS}${f}:${l}"$'\n'
  done < <(grep -nE 'fontSize = 14\.sp' "$f")
done
INPUT_14SP_COUNT=0
if [ -n "$INPUT_14SP_HITS" ]; then
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    echo "   ✗ $line"
    INPUT_14SP_COUNT=$((INPUT_14SP_COUNT + 1))
  done <<< "$INPUT_14SP_HITS"
fi
check "检查3: §3.7 输入控件正文 14sp（输入是高频读写场景，应为 16sp）" "$INPUT_14SP_COUNT"

# --- 检查 4: §3.7 密码尾缀文字化回潮（尾缀一律图标化，禁止 Text("…显示/隐藏…")）---
SUFFIX_TEXT_HITS=""
for f in "${INPUT_FILES[@]}"; do
  [ -f "$f" ] || continue
  while IFS= read -r l; do
    [ -n "$l" ] && SUFFIX_TEXT_HITS="${SUFFIX_TEXT_HITS}${f}:${l}"$'\n'
  done < <(grep -nE 'Text\(".*(显示|隐藏)' "$f")
done
SUFFIX_TEXT_COUNT=0
if [ -n "$SUFFIX_TEXT_HITS" ]; then
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    echo "   ✗ $line"
    SUFFIX_TEXT_COUNT=$((SUFFIX_TEXT_COUNT + 1))
  done <<< "$SUFFIX_TEXT_HITS"
fi
check "检查4: §3.7 密码尾缀文字「显示/隐藏」（尾缀操作一律图标化）" "$SUFFIX_TEXT_COUNT"

echo ""
if [ "$VIOLATIONS" -eq 0 ]; then
  echo "🎉 UI 规范门禁通过（M3 交互组件业务层清零 + §3.7 输入控件合规）"
  echo "   违规修复指引：.claude/ui-design-spec.md §7（交互反馈禁令）/ §3.7（输入控件）"
  echo "   —— M3 按钮 → AppButton 三态或自绘 pressable；ripple → pressScale/pressAlpha；输入正文 → 16sp；尾缀 → 图标"
  exit 0
else
  echo "⚠️  $VIOLATIONS 项违规"
  echo "   修复指引：.claude/ui-design-spec.md §7（交互反馈禁令）/ §3.7（输入控件）"
  echo "   —— M3 按钮 → AppButton 三态或自绘 pressable；ripple → pressScale/pressAlpha；输入正文 → 16sp；尾缀 → 图标"
  exit 1
fi
