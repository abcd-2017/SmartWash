#!/usr/bin/env bash
# 模块化依赖铁律检查脚本（T8.2）
#
# 铁律（方案文档 §二「依赖规则」）：
#   1. 依赖方向单向：app → feature-impl → feature-api → common → core:init
#   2. feature 之间仅允许 impl → 他人的 api（禁止 impl→impl、api→api）
#   3. core:init 零项目依赖（仅 Android SDK + Hilt + coroutines，不依赖 common）
#   4. common 不依赖任何 feature
#   5. 业务模型跟各自 api 模块走；common:model 只放真共享物
#   6. 构建配置统一由 convention plugin 收编（build-logic）
#
# 本脚本落地可静态检查的前四条；5/6 为约定，需人工 review。

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

echo "=== SmartWash-Android 依赖铁律检查 ==="
echo ""

# --- 铁律 4: common 模块不得 import feature 包 ---
COMMON_FE=0
for f in $(grep -rn --include='*.kt' -lE 'import com\.smartwash\.feature\.' common/*/src 2>/dev/null); do
  COMMON_FE=$((COMMON_FE + 1))
  echo "   ✗ $f"
done
check "铁律4: common 不引用 feature" "$COMMON_FE"

# --- 铁律 3: core:init 零项目依赖 ---
# 允许的 import 域：android.* / androidx.* / java.* / javax.* / kotlin.* / kotlinx.* /
# com.google.dagger.* / dagger.* / com.smartwash.core.init（自身）
INIT_BAD=0
for f in $(grep -rn --include='*.kt' -lE 'import com\.smartwash\.' core/init/src 2>/dev/null); do
  # 排除自身包与允许的第三方（此处无，com.smartwash 下只有 core.init 自身）
  hits=$(grep -E 'import com\.smartwash\.' "$f" | grep -v 'import com\.smartwash\.core\.init' | grep -v 'package com\.smartwash\.core\.init' || true)
  if [ -n "$hits" ]; then
    INIT_BAD=$((INIT_BAD + 1))
    echo "   ✗ $f: $(echo "$hits" | head -1)"
  fi
done
check "铁律3: core:init 零项目依赖" "$INIT_BAD"

# --- 铁律 2a: feature-api 不引用其他 feature 包（自身域内部引用合法） ---
API_FE=0
for d in feature/user/api feature/order/api feature/payment/api feature/laundry/api feature/coupon/api; do
  domain="${d#feature/}"; domain="${domain%/api}"
  [ -d "$d/src" ] || continue
  for f in $(grep -rn --include='*.kt' -lE 'import com\.smartwash\.feature\.' "$d/src" 2>/dev/null); do
    # 排除自身域（feature.<domain>.）与 package 声明
    hits=$(grep -E 'import com\.smartwash\.feature\.(user|order|payment|laundry|coupon)\.' "$f" \
           | grep -vE "import com\.smartwash\.feature\.${domain}\." || true)
    if [ -n "$hits" ]; then
      API_FE=$((API_FE + 1))
      echo "   ✗ $f: $(echo "$hits" | head -1)"
    fi
  done
done
check "铁律2a: feature-api 不引用其他 feature" "$API_FE"

# --- 铁律 2b: feature-impl 跨域引用必须经 api（禁止 impl→他人 impl） ---
IMPL_CROSS_IMPL=0
for d in feature/user/impl feature/order/impl feature/payment/impl feature/laundry/impl feature/coupon/impl; do
  domain="${d#feature/}"; domain="${domain%/impl}"   # e.g. user/order/payment/laundry/coupon
  [ -d "$d/src" ] || continue
  for f in $(grep -rn --include='*.kt' -lE 'import com\.smartwash\.feature\.[a-z]+\.impl\.' "$d/src" 2>/dev/null); do
    # 找出引用了其他域 impl 的行（排除自身域的 impl 包）
    hits=$(grep -E 'import com\.smartwash\.feature\.[a-z]+\.impl\.' "$f" \
           | grep -v "import com\.smartwash\.feature\.${domain}\.impl\." \
           || true)
    if [ -n "$hits" ]; then
      IMPL_CROSS_IMPL=$((IMPL_CROSS_IMPL + 1))
      echo "   ✗ $f: $(echo "$hits" | head -1)"
    fi
  done
done
check "铁律2b: feature-impl 不引用其他 feature-impl" "$IMPL_CROSS_IMPL"

# --- 铁律 2c: feature-impl 跨域引用必须经 api（禁止 impl→他人 api 以外的 feature 路径）---
# 等价于：impl 引用其他 feature 时，路径必须以 .api. 或 .api 模块下的包结尾
# 这里做简化：检查是否存在 impl→其他域的非 api 导入（已在 2b 覆盖 impl→impl，
# 再检查 impl 引用其他域的 model/包路径是否来自 api 模块）
IMPL_CROSS_NONAPI=0
for d in feature/user/impl feature/order/impl feature/payment/impl feature/laundry/impl feature/coupon/impl; do
  domain="${d#feature/}"; domain="${domain%/impl}"
  [ -d "$d/src" ] || continue
  for f in $(grep -rn --include='*.kt' -lE 'import com\.smartwash\.feature\.(user|order|payment|laundry|coupon)\.' "$d/src" 2>/dev/null); do
    # 排除自身域；跨域引用必须经 api 包（路径含 .api.）
    hits=$(grep -E 'import com\.smartwash\.feature\.(user|order|payment|laundry|coupon)\.' "$f" \
           | grep -vE "import com\.smartwash\.feature\.${domain}\." \
           | grep -vE 'import com\.smartwash\.feature\.[a-z]+\.api\.' \
           || true)
    if [ -n "$hits" ]; then
      IMPL_CROSS_NONAPI=$((IMPL_CROSS_NONAPI + 1))
      echo "   ✗ $f: $(echo "$hits" | head -1)"
    fi
  done
done
check "铁律2c: feature-impl 跨域引用全经 api 包" "$IMPL_CROSS_NONAPI"

echo ""
if [ "$VIOLATIONS" -eq 0 ]; then
  echo "🎉 全部依赖铁律通过"
  exit 0
else
  echo "⚠️  $VIOLATIONS 项违规"
  exit 1
fi
