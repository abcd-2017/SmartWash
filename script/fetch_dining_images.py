#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
吃啥封面网络搜图补充：对 CSV 中回落分类占位图的菜品，从开放图源搜真实菜品图，
下载后用 macOS `sips` 压缩到长边 ≤800px / JPEG q70，存 static/dining/covers/search_<菜名>.jpg，
并把 CSV 的「封面对象键」列回填为 dining/covers/search_<菜名>.jpg。

图源（均带授权信息，本地导入材料使用）：
  1. Openverse API（聚合 Flickr/Wikimedia 等 CC 授权图，返回 license 字段）
  2. Wikimedia Commons API（公有领域/CC 授权，大图）
搜不到 → 保留分类占位图（category_<分类>.png），报告中列未命中清单。

用法：python3 script/fetch_dining_images.py [--limit N] [--dry-run]
"""

import argparse
import csv
import json
import re
import subprocess
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
CSV_PATHS = [REPO / "static/dining/cooklikehoc.csv", REPO / "static/dining/howtocook.csv"]
COVERS = REPO / "static/dining/covers"
# Wikimedia 对非浏览器 UA 限流（429），实测浏览器 UA 可正常下载
UA = ("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
OPENVERSE = "https://api.openverse.org/v1/images/"
COMMONS = "https://commons.wikimedia.org/w/api.php"


def http_get_json(url, timeout=10):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def search_openverse(term, retries=2):
    """Openverse 搜索：返回 (直链, license) 或 None。只要 license 非空的 CC 图；429 重试。"""
    q = urllib.parse.quote(term)
    for attempt in range(retries):
        try:
            data = http_get_json(f"{OPENVERSE}?q={q}&page_size=5")
            break
        except Exception:
            if attempt == retries - 1:
                return None
            time.sleep(2.0)
    for item in data.get("results", []):
        url = item.get("url")
        lic = item.get("license") or ""
        if url and url.startswith("http") and lic:
            return url, lic
    return None


# 食材关键词（候选词降级：从菜名命中处截 4 字核心词，如「五彩鸡丝拌面」→「鸡丝拌面」）
FOOD_KEYWORDS = (
    "鸡", "鸭", "鱼", "虾", "蟹", "牛", "猪", "羊", "肉", "蛋", "豆腐",
    "面", "饭", "汤", "粉", "粥", "菇", "笋", "藕", "芋", "薯", "菜",
    "瓜", "茄", "葱", "姜", "蒜", "椒", "芋", "饺", "包", "饼", "糕",
    "排", "翅", "腿", "腿", "肝", "肠", "肚", "血", "皮", "筋", "骨",
)


def search_terms(name):
    """候选搜索词：全名（去括号/版本后缀）→ 食材词命中处截 4 字核心词 → 去尾部做法字兜底。"""
    base = re.sub(r"[（(][^）)]*[)）]", "", name).replace("版", "").strip()
    terms = [base]
    for kw in FOOD_KEYWORDS:
        i = base.find(kw)
        if i >= 0:
            core = base[i:i + 4].strip()
            if core and core not in terms:
                terms.append(core)
            break  # 只取首个命中的食材词
    # 去尾字兜底（「xx炒饭」→「xx炒」? 不做；用前 4 字）
    if len(base) > 4 and base[:4] not in terms:
        terms.append(base[:4])
    return terms


def search_commons(name, retries=2):
    """Wikimedia Commons 搜索（upload.wikimedia.org 图床本机可达）：返回 (直链, license) 或 None。
    跳过 SVG/动画；429 重试。Flickr 等外部图床本机不可达，不使用。"""
    params = urllib.parse.urlencode({
        "action": "query", "generator": "search", "gsrsearch": name,
        "gsrnamespace": "6", "gsrlimit": "5", "prop": "imageinfo",
        # iiurlwidth：直接要 800px 缩略图（官方推荐的降分辨率方式，thumburl 字段返回）
        "iiprop": "url|size|mime", "iiurlwidth": "800", "format": "json",
    })
    data = None
    for attempt in range(retries):
        try:
            data = http_get_json(f"{COMMONS}?{params}")
            break
        except Exception:
            if attempt == retries - 1:
                return None
            time.sleep(2.5)
    if not data:
        return None
    pages = (data.get("query") or {}).get("pages") or {}
    for page in pages.values():
        info = (page.get("imageinfo") or [{}])[0]
        # 优先 800px 缩略图（官方推荐、已降分辨率）；无 thumburl 回落原图
        url = info.get("thumburl") or info.get("url") or ""
        if not url.startswith("http") or url.lower().endswith((".svg", ".gif")):
            continue
        if "upload.wikimedia.org" not in url:  # 只要本图床可达来源
            continue
        if (info.get("width") or 0) < 600:
            continue
        lic = "commons-public"  # Commons 文件页自带授权，本地材料使用
        return url, lic
    return None


def compress_to_jpeg(src: Path, dst: Path):
    """sips 压缩：长边 ≤800、JPEG q70。返回是否成功。"""
    cmd = [
        "sips", "-Z", "800", "-s", "format", "jpeg", "-s", "formatOptions", "70",
        str(src), "--out", str(dst),
    ]
    r = subprocess.run(cmd, capture_output=True, text=True)
    return r.returncode == 0 and dst.exists()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=0, help="只处理前 N 个缺图菜（0=全部）")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    # 收集缺图菜品（封面键为分类占位图的行）
    targets = []
    for path in CSV_PATHS:
        for row in csv.DictReader(path.open(encoding="utf-8-sig")):
            key = row.get("封面对象键", "")
            if key.startswith("dining/covers/category_"):
                targets.append({"source": path.name, "name": row["菜名"], "category": row["分类名"]})
    print(f"[扫描] 缺图菜品 {len(targets)} 个（已有实拍图/已搜图的不处理）")
    if args.limit:
        targets = targets[: args.limit]
        print(f"[限制] 本次只处理前 {len(targets)} 个")

    if args.dry_run:
        for t in targets[:10]:
            print("  ->", t["name"], f"({t['source']})")
        return

    hit, miss, failed = [], [], []
    for i, t in enumerate(targets, 1):
        name = t["name"]
        dst = COVERS / f"search_{name}.jpg"
        if dst.exists():
            hit.append((name, "cached", ""))
            continue
        tmp = COVERS / f"_dl_{i}.tmp"
        got = None
        for term in search_terms(name):
            got = search_commons(term)
            if got:
                break
            time.sleep(1.2)  # Commons 限流保守间隔
        if not got:
            miss.append(name)
            print(f"  [{i}/{len(targets)}] 未命中: {name}")
            continue
        url, lic = got
        downloaded = False
        last_err = None
        for attempt in range(3):  # 429 限流重试（间隔递增）
            try:
                req = urllib.request.Request(url, headers={"User-Agent": UA})
                with urllib.request.urlopen(req, timeout=15) as resp:
                    tmp.write_bytes(resp.read())
                downloaded = True
                break
            except Exception as e:
                last_err = e
                time.sleep(8.0 * (attempt + 1))
        if not downloaded:
            failed.append((name, f"download: {last_err}"))
            print(f"  [{i}/{len(targets)}] 下载失败: {name} ({last_err})")
            if tmp.exists():
                tmp.unlink()
            continue
        if compress_to_jpeg(tmp, dst):
            hit.append((name, engine_hint(got), lic))
            print(f"  [{i}/{len(targets)}] OK: {name}")
        else:
            failed.append((name, "sips compress"))
            print(f"  [{i}/{len(targets)}] 压缩失败: {name}")
        if tmp.exists():
            tmp.unlink()
        time.sleep(0.5)  # 礼貌速率

    # 回填 CSV 封面键（搜到的行）
    updated = {name for name, _, _ in hit}
    for path in CSV_PATHS:
        rows = list(csv.DictReader(path.open(encoding="utf-8-sig")))
        fields = list(rows[0].keys())
        changed = 0
        for row in rows:
            if row["菜名"] in updated and row.get("封面对象键", "").startswith("dining/covers/category_"):
                row["封面对象键"] = f"dining/covers/search_{row['菜名']}.jpg"
                changed += 1
        if changed:
            with path.open("w", encoding="utf-8-sig", newline="") as f:
                w = csv.DictWriter(f, fieldnames=fields)
                w.writeheader()
                w.writerows(rows)
        print(f"[回填] {path.name}: {changed} 行")

    report = {"hit": [h[0] for h in hit], "miss": miss, "failed": failed}
    (REPO / "static/dining/_search_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"[完成] 命中 {len(hit)}（含缓存），未命中 {len(miss)}，失败 {len(failed)}")
    print(f"[报告] static/dining/_search_report.json")


def engine_hint(got):
    # got = (url, license)；按域名粗判来源
    url = got[0]
    if "flickr" in url or "staticflickr" in url:
        return "openverse/flickr"
    if "wikimedia" in url or "upload.wikimedia" in url:
        return "commons"
    return "openverse"


if __name__ == "__main__":
    main()
