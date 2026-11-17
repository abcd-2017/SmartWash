#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
吃啥封面批量直传 MinIO。

读取 SmartWash/src/main/resources/application.yaml 的 minio 配置
（access-key/secret-key 支持 MINIO_ACCESS_KEY / MINIO_SECRET_KEY 环境变量覆盖，
与后端同口径；凭据只读取不打印），把 static/dining/covers/ 下图片直传到
<dining covers 桶> 的 dining/covers/ 前缀下。

幂等：已存在的同名对象跳过（head_object 探测），可重复执行做断点续传。
用法：python3 script/upload_dining_covers.py [--dry-run]
"""

import argparse
import re
import sys
from pathlib import Path

import boto3
from botocore.client import Config
from botocore.exceptions import ClientError

REPO = Path(__file__).resolve().parent.parent
YAML_PATH = REPO / "SmartWash/src/main/resources/application.yaml"
COVERS_DIR = REPO / "static/dining/covers"
OBJECT_PREFIX = "dining/covers/"


def read_minio_config():
    """从 application.yaml 读取 minio 配置（环境变量优先，与 Spring 占位符同语义）。"""
    text = YAML_PATH.read_text(encoding="utf-8")
    block = text.split("minio:", 1)[1].split("\n\n", 1)[0] if "minio:" in text else ""

    def grab(key, env):
        if env_val := __import__("os").environ.get(env):
            return env_val
        m = re.search(rf"{key}:\s*\$\{{{env}([^}}]*)\}}|{key}:\s*(\S+)", block)
        if not m:
            return None
        if m.group(1) is not None:  # ${ENV:default} 形式
            default = m.group(1)
            default = default[1:] if default.startswith(":") else default
            default = default.strip().strip('"').strip("'")
            return default or None
        return m.group(2).strip().strip('"').strip("'")

    return {
        "endpoint": grab("endpoint", "MINIO_ENDPOINT") or "http://192.168.1.61:9000",
        "access_key": grab("access-key", "MINIO_ACCESS_KEY"),
        "secret_key": grab("secret-key", "MINIO_SECRET_KEY"),
        "bucket": grab("bucket-name", "MINIO_BUCKET") or "smartwash",
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="只列出待上传项，不上传")
    args = parser.parse_args()

    cfg = read_minio_config()
    if not cfg["access_key"] or not cfg["secret_key"]:
        print("[失败] 未找到 MinIO 凭据（yaml 或 MINIO_ACCESS_KEY/MINIO_SECRET_KEY）", file=sys.stderr)
        sys.exit(1)
    print(f"[配置] endpoint={cfg['endpoint']} bucket={cfg['bucket']}（凭据不打印）")

    images = sorted(p for p in COVERS_DIR.iterdir() if p.suffix.lower() in (".jpg", ".jpeg", ".png", ".webp"))
    print(f"[扫描] covers/ 共 {len(images)} 张")

    if args.dry_run:
        for p in images[:10]:
            print("  ->", OBJECT_PREFIX + p.name)
        print(f"  ...共 {len(images)} 项")
        return

    s3 = boto3.client(
        "s3",
        endpoint_url=cfg["endpoint"],
        aws_access_key_id=cfg["access_key"],
        aws_secret_access_key=cfg["secret_key"],
        region_name="us-east-1",
        config=Config(s3={"addressing_style": "path"}, retries={"max_attempts": 3}),
    )

    # 桶探测
    try:
        s3.head_bucket(Bucket=cfg["bucket"])
    except ClientError as e:
        print(f"[失败] 桶 {cfg['bucket']} 不可达：{e.response.get('Error', {}).get('Code')}", file=sys.stderr)
        sys.exit(1)

    uploaded, skipped, failed = 0, 0, []
    for p in images:
        key = OBJECT_PREFIX + p.name
        try:
            s3.head_object(Bucket=cfg["bucket"], Key=key)
            skipped += 1
            continue
        except ClientError:
            pass
        try:
            s3.upload_file(str(p), cfg["bucket"], key)
            uploaded += 1
        except ClientError as e:
            failed.append((p.name, str(e)))
            print(f"  [失败] {p.name}: {e}", file=sys.stderr)

    print(f"[完成] 上传 {uploaded}，已存在跳过 {skipped}，失败 {len(failed)}")
    if uploaded:
        print(f"[提示] 对象键规则 dining/covers/<文件名>，可直接作为导入 CSV 的封面对象键列")


if __name__ == "__main__":
    main()
