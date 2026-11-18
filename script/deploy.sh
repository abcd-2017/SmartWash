#!/usr/bin/env bash

set -Eeuo pipefail

# ============================================================
# SmartWash Deploy Script
#
# 用法：
#   /opt/smartwash/deploy.sh v1.0.1
#
# 由 GitHub Actions webhook 触发，接收版本号，
# 从 GitHub Release 下载 APK + version.json，
# 上传 APK 到 MinIO，部署 version.json 到后端 config 卷，
# 拉取镜像并滚动更新，自动清理旧镜像避免磁盘堆积。
# ============================================================

# ============================================================
# 基础配置（全部走环境变量，无默认值）
# ============================================================

# 先加载 .env（APP_DIR/ENV_FILE 为定位 .env 所需，允许外部覆盖）
APP_DIR="${APP_DIR:-/opt/smartwash}"
ENV_FILE="${ENV_FILE:-${APP_DIR}/.env}"
if [[ -f "$ENV_FILE" ]]; then
    # shellcheck disable=SC1090
    source "$ENV_FILE"
fi

COMPOSE_FILE="${COMPOSE_FILE:?COMPOSE_FILE 未配置}"
LOG_FILE="${LOG_FILE:?LOG_FILE 未配置}"

# GitHub
GITHUB_REPO="${GITHUB_REPO:?GITHUB_REPO 未配置}"
GITHUB_API="${GITHUB_API:?GITHUB_API 未配置}"
GITHUB_PROXY="${GITHUB_PROXY:?GITHUB_PROXY 未配置}"

# ACR
BACKEND_IMAGE="${BACKEND_IMAGE:?BACKEND_IMAGE 未配置}"
WEB_IMAGE="${WEB_IMAGE:?WEB_IMAGE 未配置}"

# MinIO
MINIO_ENDPOINT="${MINIO_ENDPOINT:?MINIO_ENDPOINT 未配置}"
MINIO_ALIAS="${MINIO_ALIAS:?MINIO_ALIAS 未配置}"
MINIO_BUCKET="${MINIO_BUCKET:?MINIO_BUCKET 未配置}"
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:?MINIO_ACCESS_KEY 未配置}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:?MINIO_SECRET_KEY 未配置}"

# APK 对象前缀（须与后端 app.apk.object-prefix / object-prefix-lite 一致）
MINIO_APK_PATH="${MINIO_APK_PATH:?MINIO_APK_PATH 未配置}"
MINIO_APK_PATH_LITE="${MINIO_APK_PATH_LITE:?MINIO_APK_PATH_LITE 未配置}"

MC="${MC:?MC 未配置}"

# 后端 config 卷（容器内 version.json 存放位置）
CONFIG_DIR="${CONFIG_DIR:?CONFIG_DIR 未配置}"
RELEASE_DIR="${RELEASE_DIR:?RELEASE_DIR 未配置}"

# 旧版本镜像保留数量（超过此数量的最早版本 tag 将被清理）
KEEP_VERSIONS="${KEEP_VERSIONS:?KEEP_VERSIONS 未配置}"

# ============================================================
# 可配置项
# ============================================================

# 健康检查：默认探本机后端的公开版本接口（无需鉴权，现读 version.json）
HEALTH_ENDPOINT="${HEALTH_ENDPOINT:-/web/app/version}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-120}"
HEALTH_INTERVAL="${HEALTH_INTERVAL:-5}"
# 健康检查失败是否阻断部署（true=阻断；false=只告警，用于端点未就绪时临时放行）
HEALTH_REQUIRED="${HEALTH_REQUIRED:-true}"

BACKEND_PORT="${BACKEND_PORT:?BACKEND_PORT 未配置}"
WEB_PORT="${WEB_PORT:?WEB_PORT 未配置}"

# 地址允许只写路径，自动补本机后端前缀
case "$HEALTH_ENDPOINT" in
    http://* | https://*) ;;
    *) HEALTH_ENDPOINT="http://127.0.0.1:${BACKEND_PORT}${HEALTH_ENDPOINT}" ;;
esac
# ============================================================
# 参数
# ============================================================

VERSION="${1:-}"

if [[ -z "$VERSION" ]]; then
    echo "ERROR: version is required"
    echo "Usage: $0 v1.0.1"
    exit 1
fi

# ============================================================
# 日志
# ============================================================

mkdir -p "$APP_DIR"

exec >> "$LOG_FILE" 2>&1

echo ""
echo "============================================================"
echo "Deployment started"
echo "Time    : $(date '+%Y-%m-%d %H:%M:%S')"
echo "Version : ${VERSION}"
echo "============================================================"

# ============================================================
# 基础检查
# ============================================================

if [[ ! "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+([-._a-zA-Z0-9]+)?$ ]]; then
    echo "ERROR: invalid version: ${VERSION}"
    exit 1
fi

for command in curl jq docker sha256sum; do
    if ! command -v "$command" >/dev/null 2>&1; then
        echo "ERROR: required command not found: $command"
        exit 1
    fi
done

if [[ ! -x "$MC" ]]; then
    echo "ERROR: MinIO Client not found: ${MC}"
    exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
    echo "ERROR: env file not found: ${ENV_FILE}"
    exit 1
fi

if [[ ! -f "$COMPOSE_FILE" ]]; then
    echo "ERROR: compose file not found: ${COMPOSE_FILE}"
    exit 1
fi

if ! docker info >/dev/null 2>&1; then
    echo "ERROR: Docker is not available"
    exit 1
fi

# ============================================================
# GitHub Token
# ============================================================

GITHUB_TOKEN_FILE="${APP_DIR}/github-token"

if [[ -f "$GITHUB_TOKEN_FILE" ]]; then
    GITHUB_TOKEN="$(cat "$GITHUB_TOKEN_FILE")"
else
    GITHUB_TOKEN=""
fi

if [[ -n "$GITHUB_TOKEN" ]]; then
    AUTH_HEADER=(
        -H "Authorization: Bearer ${GITHUB_TOKEN}"
    )
else
    AUTH_HEADER=()
fi

# ============================================================
# 1. 获取 GitHub Release
# ============================================================

echo "[1/9] Fetching GitHub Release..."

RELEASE_JSON="/tmp/smartwash-release-${VERSION}.json"

HTTP_CODE=$(
    curl \
        --silent \
        --show-error \
        --location \
        --retry 5 \
        --retry-delay 2 \
        --retry-all-errors \
        --connect-timeout 10 \
        --max-time 60 \
        --output "$RELEASE_JSON" \
        --write-out "%{http_code}" \
        "${AUTH_HEADER[@]}" \
        -H "Accept: application/vnd.github+json" \
        "${GITHUB_API}/repos/${GITHUB_REPO}/releases/tags/${VERSION}"
)

if [[ "$HTTP_CODE" != "200" ]]; then
    echo "ERROR: failed to fetch GitHub Release"
    echo "HTTP status: ${HTTP_CODE}"

    if [[ -f "$RELEASE_JSON" ]]; then
        cat "$RELEASE_JSON"
    fi

    exit 1
fi

RELEASE_TAG="$(jq -r '.tag_name // empty' "$RELEASE_JSON")"

if [[ "$RELEASE_TAG" != "$VERSION" ]]; then
    echo "ERROR: release tag mismatch"
    echo "Expected: ${VERSION}"
    echo "Actual  : ${RELEASE_TAG}"
    exit 1
fi

# ============================================================
# 2. 下载 version.json
# ============================================================

echo "[2/9] Downloading version.json..."

VERSION_JSON_URL="$(
    jq -r '
        .assets[]
        | select(.name == "version.json")
        | .browser_download_url
    ' "$RELEASE_JSON"
)"

if [[ -z "$VERSION_JSON_URL" || "$VERSION_JSON_URL" == "null" ]]; then
    echo "ERROR: version.json not found"
    exit 1
fi

VERSION_JSON="/tmp/smartwash-version-${VERSION}.json"

VERSION_PROXY_URL="${GITHUB_PROXY}/${VERSION_JSON_URL}"

curl \
    --fail-with-body \
    --silent \
    --show-error \
    --location \
    --retry 5 \
    --retry-delay 2 \
    --retry-all-errors \
    --connect-timeout 10 \
    --max-time 60 \
    -o "$VERSION_JSON" \
    "$VERSION_PROXY_URL"

if [[ ! -s "$VERSION_JSON" ]]; then
    echo "ERROR: version.json download failed"
    exit 1
fi

if ! jq empty "$VERSION_JSON" >/dev/null 2>&1; then
    echo "ERROR: invalid version.json"
    exit 1
fi

# ============================================================
# 校验版本
# ============================================================

RELEASE_VERSION="$(jq -r '.versionName // empty' "$VERSION_JSON")"

if [[ "$RELEASE_VERSION" != "${VERSION#v}" ]]; then
    echo "ERROR: version mismatch"
    echo "Expected: ${VERSION#v}"
    echo "Actual  : ${RELEASE_VERSION}"
    exit 1
fi

# ============================================================
# 读取发布信息
# ============================================================

APK_FILE_NAME="$(jq -r '.fileName // empty' "$VERSION_JSON")"
APK_SHA256="$(jq -r '.sha256 // empty' "$VERSION_JSON")"

BACKEND_DIGEST="$(jq -r '.backendDigest // empty' "$VERSION_JSON")"
WEB_DIGEST="$(jq -r '.webDigest // empty' "$VERSION_JSON")"

# ============================================================
# APK 参数校验
# ============================================================

if [[ -z "$APK_FILE_NAME" || "$APK_FILE_NAME" == "null" ]]; then
    echo "ERROR: APK fileName is missing"
    exit 1
fi

if [[ ! "$APK_FILE_NAME" =~ ^[a-zA-Z0-9._-]+\.apk$ ]]; then
    echo "ERROR: invalid APK file name: $APK_FILE_NAME"
    exit 1
fi

if [[ ! "$APK_SHA256" =~ ^[a-fA-F0-9]{64}$ ]]; then
    echo "ERROR: invalid APK SHA256"
    exit 1
fi

# ---- lite 渠道（channels.lite）----
# 兼容旧清单：无 channels 节点时 LITE_FILE_NAME 为空，跳过 lite 上传
LITE_FILE_NAME="$(jq -r '.channels.lite.fileName // empty' "$VERSION_JSON")"
LITE_SHA256="$(jq -r '.channels.lite.sha256 // empty' "$VERSION_JSON")"
LITE_FILE=""

if [[ -n "$LITE_FILE_NAME" ]]; then
    if [[ ! "$LITE_FILE_NAME" =~ ^[a-zA-Z0-9._-]+\.apk$ ]]; then
        echo "ERROR: invalid lite APK file name: $LITE_FILE_NAME"
        exit 1
    fi
    if [[ ! "$LITE_SHA256" =~ ^[a-fA-F0-9]{64}$ ]]; then
        echo "ERROR: invalid lite APK SHA256"
        exit 1
    fi
fi

# ============================================================
# Digest 校验
# ============================================================

validate_digest() {
    local digest="$1"

    if [[ ! "$digest" =~ ^sha256:[a-fA-F0-9]{64}$ ]]; then
        echo "ERROR: invalid digest:"
        echo "$digest"
        exit 1
    fi
}

validate_digest "$BACKEND_DIGEST"
validate_digest "$WEB_DIGEST"

# ============================================================
# 最终镜像引用
# ============================================================

BACKEND_REF="${BACKEND_IMAGE}@${BACKEND_DIGEST}"
WEB_REF="${WEB_IMAGE}@${WEB_DIGEST}"

echo ""
echo "Backend:"
echo "  ${BACKEND_REF}"

echo "Web:"
echo "  ${WEB_REF}"

# ============================================================
# 获取当前运行容器 Digest
# ============================================================

get_container_digest() {
    local container="$1"
    local image

    if ! docker inspect "$container" >/dev/null 2>&1; then
        return 1
    fi

    image="$(
        docker inspect \
            --format '{{ .Config.Image }}' \
            "$container"
    )"

    docker image inspect "$image" \
        --format '{{ index .RepoDigests 0 }}' \
        2>/dev/null | \
        sed 's/.*@//'
}

CURRENT_BACKEND_DIGEST="$(
    get_container_digest smartwash-backend || true
)"

CURRENT_WEB_DIGEST="$(
    get_container_digest smartwash-web || true
)"

get_image_id() {
    local image_ref="$1"
    docker image inspect "$image_ref" --format '{{.Id}}' 2>/dev/null || echo ""
}

# ============================================================
# 3. 比较 Digest
# ============================================================

echo "[3/9] Comparing image digest..."

echo ""
echo "Current backend: ${CURRENT_BACKEND_DIGEST:-none}"
echo "Target  backend: ${BACKEND_DIGEST}"

echo "Current web    : ${CURRENT_WEB_DIGEST:-none}"
echo "Target  web    : ${WEB_DIGEST}"

BACKEND_CHANGED=false
WEB_CHANGED=false

if [[ "$CURRENT_BACKEND_DIGEST" != "$BACKEND_DIGEST" ]]; then
    BACKEND_CHANGED=true
fi

if [[ "$CURRENT_WEB_DIGEST" != "$WEB_DIGEST" ]]; then
    WEB_CHANGED=true
fi

# ============================================================
# 记录当前本地镜像 ID（pull 并滚动更新后会变成虚悬 / 过期版本）
# 必须在确定变更后、pull 新镜像前记录，否则旧镜像 ID 丢失
# ============================================================

OLD_BACKEND_ID=""
OLD_WEB_ID=""

if [[ "$BACKEND_CHANGED" == true ]]; then
    OLD_BACKEND_ID="$(get_image_id "$BACKEND_REF")"
fi

if [[ "$WEB_CHANGED" == true ]]; then
    OLD_WEB_ID="$(get_image_id "$WEB_REF")"
fi

# ============================================================
# 4. 下载 APK（按渠道：full 必有，lite 视清单而定）
# ============================================================

echo "[4/9] Downloading APK..."

download_apk() {
    local file_name="$1"
    local label="$2"
    local url file_path proxy_url

    url="$(
        jq -r '
            .assets[]
            | select(.name == "'"${file_name}"'")
            | .browser_download_url
        ' "$RELEASE_JSON"
    )"

    if [[ -z "$url" || "$url" == "null" ]]; then
        echo "ERROR: ${label} APK not found in GitHub Release: ${file_name}" >&2
        exit 1
    fi

    file_path="/tmp/${file_name}"
    proxy_url="${GITHUB_PROXY}/${url}"

    # 已完整存在（SHA256 与清单一致）则不重复下载；校验值由调用方经 EXPECTED_SHA 传入
    if [[ -f "$file_path" ]]; then
        local existing_sha
        existing_sha="$(sha256sum "$file_path" | awk '{print $1}')"
        if [[ "$existing_sha" == "${EXPECTED_SHA:-}" ]]; then
            # 日志走 stderr：本函数的 stdout 会被调用方命令替换捕获为文件路径
            echo "Existing ${label} APK is valid, skip download." >&2
        else
            rm -f "$file_path"
        fi
    fi

    if [[ ! -f "$file_path" ]]; then
        echo "[${label}] downloading ${file_name}..." >&2
        curl \
            --fail-with-body \
            --silent \
            --show-error \
            --location \
            --continue-at - \
            --retry 10 \
            --retry-delay 3 \
            --retry-all-errors \
            --connect-timeout 10 \
            --max-time 600 \
            -o "$file_path" \
            "$proxy_url"
    fi

    if [[ ! -s "$file_path" ]]; then
        echo "ERROR: ${label} APK download failed" >&2
        exit 1
    fi

    # 唯一走 stdout 的内容：文件路径（供调用方捕获）
    echo "$file_path"
}

EXPECTED_SHA="$APK_SHA256"
APK_FILE="$(download_apk "$APK_FILE_NAME" "full")"

if [[ -n "$LITE_FILE_NAME" ]]; then
    EXPECTED_SHA="$LITE_SHA256"
    LITE_FILE="$(download_apk "$LITE_FILE_NAME" "lite")"
fi

# ============================================================
# 5. 校验 APK SHA256
# ============================================================

echo "[5/9] Verifying APK SHA256..."

verify_sha() {
    local file_path="$1" expected="$2" label="$3" actual
    actual="$(sha256sum "$file_path" | awk '{print $1}')"
    echo "[${label}] Expected: ${expected}"
    echo "[${label}] Actual  : ${actual}"
    if [[ "$actual" != "$expected" ]]; then
        echo "ERROR: ${label} APK SHA256 mismatch"
        rm -f "$file_path"
        exit 1
    fi
    echo "[${label}] SHA256 verified."
}

verify_sha "$APK_FILE" "$APK_SHA256" "full"

if [[ -n "$LITE_FILE" ]]; then
    verify_sha "$LITE_FILE" "$LITE_SHA256" "lite"
fi

# ============================================================
# 6. 上传 MinIO
# ============================================================

echo "[6/9] Uploading APK to MinIO..."

export "MC_HOST_${MINIO_ALIAS}=http://${MINIO_ACCESS_KEY}:${MINIO_SECRET_KEY}@${MINIO_ENDPOINT#http://}"

# Bucket 不存在就创建，存在则忽略
"$MC" mb \
    --ignore-existing \
    "${MINIO_ALIAS}/${MINIO_BUCKET}" \
    >/dev/null

upload_to_minio() {
    local file_path="$1" prefix="$2" file_name="$3" label="$4" object
    object="${MINIO_ALIAS}/${MINIO_BUCKET}/${prefix}/${file_name}"
    "$MC" cp "$file_path" "$object"
    echo "[${label}] MinIO object: ${object}"
    "$MC" stat "$object" >/dev/null
    echo "[${label}] MinIO object verified."
}

upload_to_minio "$APK_FILE" "$MINIO_APK_PATH" "$APK_FILE_NAME" "full"

if [[ -n "$LITE_FILE" ]]; then
    upload_to_minio "$LITE_FILE" "$MINIO_APK_PATH_LITE" "$LITE_FILE_NAME" "lite"
fi

# ============================================================
# 部署 version.json 到后端 config 卷
# ============================================================

echo "Deploying version.json to backend config volume..."

mkdir -p "$RELEASE_DIR"
cp "$VERSION_JSON" "$RELEASE_DIR/version.json"

echo "version.json deployed to:"
echo "${RELEASE_DIR}/version.json"

# ============================================================
# 如果镜像没有变化
# ============================================================

if [[ "$BACKEND_CHANGED" == false && "$WEB_CHANGED" == false ]]; then

    echo ""
    echo "Current deployment already matches target digest."
    echo "Images do not need to be deployed."

    rm -f "$APK_FILE"
    [[ -n "${LITE_FILE:-}" ]] && rm -f "$LITE_FILE"
    rm -f "$VERSION_JSON"

    echo ""
    echo "Deployment completed."
    exit 0
fi

# ============================================================
# 7. Pull 镜像
# ============================================================

echo "[7/9] Pulling images..."

if [[ "$BACKEND_CHANGED" == true ]]; then

    echo "Pulling backend..."

    docker pull "$BACKEND_REF"

else

    echo "Backend digest unchanged, skip pull."

fi

if [[ "$WEB_CHANGED" == true ]]; then

    echo "Pulling web..."

    docker pull "$WEB_REF"

else

    echo "Web digest unchanged, skip pull."

fi

# ============================================================
# 验证实际镜像 Digest
# ============================================================

echo "Verifying image digest..."

verify_image_digest() {
    local image_ref="$1"
    local expected_digest="$2"

    local actual_digest

    actual_digest="$(
        docker image inspect "$image_ref" \
            --format '{{ index .RepoDigests 0 }}' \
            2>/dev/null | \
        sed 's/.*@//'
    )"

    if [[ "$actual_digest" != "$expected_digest" ]]; then

        echo "ERROR: image digest mismatch"

        echo "Expected:"
        echo "${expected_digest}"

        echo "Actual:"
        echo "${actual_digest}"

        exit 1
    fi
}

verify_image_digest "$BACKEND_REF" "$BACKEND_DIGEST"
verify_image_digest "$WEB_REF" "$WEB_DIGEST"

# ============================================================
# 生成 release.env
# ============================================================

RELEASE_ENV="${APP_DIR}/release.env"

cat > "$RELEASE_ENV" <<EOF
VERSION=${VERSION}
BACKEND_IMAGE=${BACKEND_REF}
WEB_IMAGE=${WEB_REF}
DEPLOY_TIME=$(date '+%Y-%m-%d %H:%M:%S')
EOF

chmod 600 "$RELEASE_ENV"

# ============================================================
# 8. Docker Compose
# ============================================================

echo "[8/9] Updating application containers..."

docker compose \
    --env-file "$ENV_FILE" \
    --env-file "$RELEASE_ENV" \
    -f "$COMPOSE_FILE" \
    up -d \
    --no-build

# ------------------------------------------------------------
# 8b. 健康检查（容器起来 ≠ 服务可用，可用才算部署成功）
# ------------------------------------------------------------

echo "Waiting for service health..."

HEALTH_CODE=""
HEALTH_DEADLINE=$((SECONDS + HEALTH_TIMEOUT))

while (( SECONDS < HEALTH_DEADLINE )); do
    HEALTH_CODE="$(
        curl \
            --silent \
            --show-error \
            --output /dev/null \
            --write-out '%{http_code}' \
            "$HEALTH_ENDPOINT" \
            || true
    )"

    if [[ "$HEALTH_CODE" =~ ^2[0-9][0-9]$ ]]; then
        echo "Health check passed (HTTP ${HEALTH_CODE})."
        break
    fi

    sleep "$HEALTH_INTERVAL"
done

if [[ ! "$HEALTH_CODE" =~ ^2[0-9][0-9]$ ]]; then
    echo "ERROR: health check failed after ${HEALTH_TIMEOUT}s (last HTTP ${HEALTH_CODE:-no response})"
    echo "Endpoint: ${HEALTH_ENDPOINT}"

    if [[ "$HEALTH_REQUIRED" == "true" ]]; then
        exit 1
    fi

    echo "WARNING: HEALTH_REQUIRED=false，健康检查未通过仍继续部署。"
fi

# ============================================================
# 9. 清理旧镜像（核心：解决虚悬镜像堆积问题）
# ============================================================

echo "[9/9] Cleaning up old Docker images..."

# --- 9a. 删除 pull 前记录的旧镜像（此时容器已切换到新镜像，旧镜像可安全删除）---
if [[ -n "$OLD_BACKEND_ID" ]]; then
    echo "Removing old backend image: ${OLD_BACKEND_ID}"
    docker rmi "$OLD_BACKEND_ID" 2>/dev/null || true
fi

if [[ -n "$OLD_WEB_ID" ]]; then
    echo "Removing old web image: ${OLD_WEB_ID}"
    docker rmi "$OLD_WEB_ID" 2>/dev/null || true
fi

# --- 9b. 清理所有虚悬镜像（<none>:<none>）---
echo "Pruning dangling images..."
docker image prune -f

# --- 9c. 清理超过保留数量的旧版本 tag（保留最近 N 个版本，始终保留 latest）---
cleanup_old_versions() {
    local image_name="$1"
    local keep="${2:-$KEEP_VERSIONS}"

    # 列出所有具名版本 tag（排除 latest 和 <none>），按创建时间倒序
    # 注意：grep -v 在无匹配行时退出码为 1，pipefail 会传播失败导致 set -e 退出脚本
    # 必须加 || true 兜底，否则 digest 拉取的镜像（tag 为 <none>）会让管道必然失败
    local old_tags
    old_tags="$(
        docker image ls "$image_name" \
            --format '{{.Tag}}\t{{.CreatedAt}}' \
            | grep -v -e '^latest' -e '^<none>' \
            | sort -k2 -r \
            | tail -n +"$((keep + 1))" \
            | awk '{print $1}'
    )" || true

    if [[ -n "$old_tags" ]]; then
        echo "Removing old version tags for ${image_name}:"
        echo "$old_tags"
        # shellcheck disable=SC2086
        echo "$old_tags" | xargs -r -I {} docker rmi "${image_name}:{}" 2>/dev/null || true
    else
        echo "No old versions to clean for ${image_name}."
    fi
}

cleanup_old_versions "$BACKEND_IMAGE" "$KEEP_VERSIONS"
cleanup_old_versions "$WEB_IMAGE" "$KEEP_VERSIONS"

# --- 9d. 最终虚悬清理（删除旧版本 tag 后可能产生新的虚悬层）---
docker image prune -f

# --- 9e. 磁盘使用报告 ---
echo ""
echo "Docker disk usage after cleanup:"
docker system df

# ============================================================
# 清理临时文件
# ============================================================

rm -f "$APK_FILE"
[[ -n "${LITE_FILE:-}" ]] && rm -f "$LITE_FILE"
rm -f "$VERSION_JSON"

# ============================================================
# 完成
# ============================================================

echo ""

echo "============================================================"
echo "Deployment successful"
echo "Version : ${VERSION}"
echo "Time    : $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================================"
