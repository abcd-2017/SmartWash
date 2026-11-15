#!/bin/sh
# ============================================================
#  SmartWash-Android 模块脚手架（Unix / macOS / Git Bash）
#  薄包装：调用同目录 scaffold-module.py
#
#  用法:
#    ./scripts/scaffold-module.sh --type feature --name game
#    ./scripts/scaffold-module.sh --type feature --name order --split
#    ./scripts/scaffold-module.sh --type common --name util --with-compose
#    ./scripts/scaffold-module.sh --type feature --name game --delete --yes
#    ./scripts/scaffold-module.sh --type feature --name demo --dry-run
# ============================================================
set -e

DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

if command -v python3 >/dev/null 2>&1; then
    PY=python3
elif command -v python >/dev/null 2>&1; then
    PY=python
else
    echo "[ERROR] 未找到 python3/python，请先安装 Python 3" >&2
    exit 1
fi

exec "$PY" "$DIR/scaffold-module.py" "$@"
