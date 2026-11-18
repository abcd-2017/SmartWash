use axum::{
    extract::{Query, State},
    http::{HeaderMap, StatusCode},
    routing::{get, post},
    Json, Router,
};
use serde::{Deserialize, Serialize};
use std::{
    env, fs,
    path::{Path, PathBuf},
    process::Stdio,
    sync::Arc,
    time::{Duration, SystemTime, UNIX_EPOCH},
};
use tokio::process::Command;
use tracing::{error, info, warn};

const DEPLOY_SCRIPT: &str = "/opt/smartwash/deploy.sh";
const DEPLOY_LOG: &str = "/opt/smartwash/deploy.log";
const DEFAULT_STATUS_DIR: &str = "/opt/smartwash/deploy-status";
const DEFAULT_LISTEN_ADDR: &str = "0.0.0.0:18080";
const DEFAULT_STALE_TIMEOUT_SECS: u64 = 1800;
const DEFAULT_STATUS_RETAIN_DAYS: u64 = 30;

#[derive(Clone)]
struct AppState {
    deploy_token: Arc<String>,
    deploy_script: String,
    deploy_log: String,
    status_dir: PathBuf,
    /// 超过此时长的 running 记录视为僵尸（服务重启等原因），允许新部署
    stale_timeout: Duration,
    /// 状态文件保留时长，超出后清理，避免目录无限增长
    status_retain: Duration,
}

#[derive(Debug, Deserialize)]
struct DeployRequest {
    version: String,
    #[serde(rename = "backendImage")]
    backend_image: String,
    #[serde(rename = "backendDigest")]
    backend_digest: String,
    #[serde(rename = "webImage")]
    web_image: String,
    #[serde(rename = "webDigest")]
    web_digest: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct DeployStatus {
    id: String,
    version: String,
    /// running | success | failed
    state: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    message: Option<String>,
    #[serde(default)]
    started_at: u64,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    finished_at: Option<u64>,
}

#[derive(Debug, Deserialize)]
struct StatusQuery {
    id: String,
}

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();

    let deploy_token = env::var("DEPLOY_TOKEN")
        .expect("DEPLOY_TOKEN environment variable is required");

    let deploy_script = env::var("DEPLOY_SCRIPT").unwrap_or_else(|_| DEPLOY_SCRIPT.to_string());
    let deploy_log = env::var("DEPLOY_LOG").unwrap_or_else(|_| DEPLOY_LOG.to_string());
    let listen_addr = env::var("DEPLOY_LISTEN_ADDR").unwrap_or_else(|_| DEFAULT_LISTEN_ADDR.to_string());

    let status_dir = env::var("DEPLOY_STATUS_DIR").unwrap_or_else(|_| DEFAULT_STATUS_DIR.to_string());

    let stale_timeout = env::var("DEPLOY_STALE_TIMEOUT_SECS")
        .ok()
        .and_then(|v| v.parse::<u64>().ok())
        .filter(|v| *v > 0)
        .unwrap_or(DEFAULT_STALE_TIMEOUT_SECS);

    let status_retain_days = env::var("DEPLOY_STATUS_RETAIN_DAYS")
        .ok()
        .and_then(|v| v.parse::<u64>().ok())
        .filter(|v| *v > 0)
        .unwrap_or(DEFAULT_STATUS_RETAIN_DAYS);

    let state = AppState {
        deploy_token: Arc::new(deploy_token),
        deploy_script,
        deploy_log,
        status_dir: PathBuf::from(status_dir),
        stale_timeout: Duration::from_secs(stale_timeout),
        status_retain: Duration::from_secs(status_retain_days.saturating_mul(86400)),
    };

    info!(
        deploy_script = %state.deploy_script,
        status_dir = %state.status_dir.display(),
        stale_timeout_secs = stale_timeout,
        "Deploy service preparing on {}", listen_addr
    );

    let app = Router::new()
        .route("/api/deploy", post(deploy))
        .route("/api/deploy/status", get(status))
        .with_state(state);

    info!("Deploy service listening on {}", listen_addr);

    let listener = tokio::net::TcpListener::bind(&listen_addr)
        .await
        .expect("failed to bind");

    axum::serve(listener, app)
        .await
        .expect("server error");
}

/// 触发部署：校验后立即返回 202，deploy.sh 在后台执行，
/// 调用方通过 /api/deploy/status 轮询真实结果。
async fn deploy(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(request): Json<DeployRequest>,
) -> Result<(StatusCode, Json<serde_json::Value>), (StatusCode, String)> {
    check_auth(&headers, &state.deploy_token)?;

    if !is_valid_version(&request.version) {
        return Err((StatusCode::BAD_REQUEST, "invalid version".to_string()));
    }

    if !is_valid_digest(&request.backend_digest) || !is_valid_digest(&request.web_digest) {
        return Err((
            StatusCode::BAD_REQUEST,
            "invalid image digest".to_string(),
        ));
    }

    let backend_image = format!("{}@{}", request.backend_image, request.backend_digest);
    let web_image = format!("{}@{}", request.web_image, request.web_digest);

    info!(
        version = %request.version,
        backend = %backend_image,
        web = %web_image,
        "deployment requested"
    );

    // -----------------------------------------------------
    // 并发保护：同一时刻只允许一个部署在跑
    // -----------------------------------------------------
    let running = running_deploy(&state.status_dir).map_err(|e| {
        error!("failed to scan deploy status dir: {}", e);
        (
            StatusCode::INTERNAL_SERVER_ERROR,
            "failed to inspect deploy state".to_string(),
        )
    })?;

    if let Some((_, running, mtime)) = running {
        let age = mtime.elapsed().unwrap_or(Duration::ZERO);
        if age < state.stale_timeout {
            return Err((StatusCode::CONFLICT, format!(
                "another deployment is in progress: {} (version {})",
                running.id, running.version
            )));
        }

        warn!(id = %running.id, age_secs = age.as_secs(), "stale running deployment entry, marking failed");
        let stale = DeployStatus {
            state: "failed".to_string(),
            message: Some(
                "marked failed: stale running entry (deploy service restarted?)".to_string(),
            ),
            finished_at: Some(now_millis()),
            ..running
        };
        write_status(&state.status_dir, &stale).map_err(|e| {
            error!("failed to write stale deploy status: {}", e);
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                "failed to update deploy state".to_string(),
            )
        })?;
    }

    // -----------------------------------------------------
    // 先落 running 状态，再启动后台部署
    // -----------------------------------------------------
    let now = now_millis();
    let id = format!("{}-{}", request.version, now);

    let running_status = DeployStatus {
        id: id.clone(),
        version: request.version.clone(),
        state: "running".to_string(),
        message: None,
        started_at: now,
        finished_at: None,
    };

    write_status(&state.status_dir, &running_status).map_err(|e| {
        error!("failed to write running deploy status: {}", e);
        (
            StatusCode::INTERNAL_SERVER_ERROR,
            "failed to persist deploy state".to_string(),
        )
    })?;

    cleanup_old_status(&state.status_dir, state.status_retain);

    let mut child = Command::new(&state.deploy_script)
        .arg(&request.version)
        .arg(&backend_image)
        .arg(&web_image)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn()
        .map_err(|e| {
            error!("failed to execute deploy.sh: {}", e);
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                "failed to start deploy".to_string(),
            )
        })?;

    // 后台等待子进程，结束后回写终态，避免僵尸进程
    let monitor_dir = state.status_dir.clone();
    let monitor_log = state.deploy_log.clone();
    tokio::spawn(async move {
        let status = child.wait().await;

        let final_status = match status {
            Ok(s) if s.success() => DeployStatus {
                state: "success".to_string(),
                message: None,
                finished_at: Some(now_millis()),
                ..running_status
            },
            Ok(s) => {
                let code = s
                    .code()
                    .map(|c| c.to_string())
                    .unwrap_or_else(|| "terminated by signal".to_string());
                let mut message = format!("deployment failed (exit {})", code);

                let tail = tail_log(Path::new(&monitor_log), 40);
                if !tail.is_empty() {
                    message.push_str("\n\nLast log lines:\n");
                    message.push_str(&tail);
                }

                DeployStatus {
                    state: "failed".to_string(),
                    message: Some(message),
                    finished_at: Some(now_millis()),
                    ..running_status
                }
            }
            Err(e) => DeployStatus {
                state: "failed".to_string(),
                message: Some(format!("failed to wait for deploy process: {}", e)),
                finished_at: Some(now_millis()),
                ..running_status
            },
        };

        info!(id = %final_status.id, state = %final_status.state, "deployment finished");

        if let Err(e) = write_status(&monitor_dir, &final_status) {
            error!(id = %final_status.id, "failed to write final deploy status: {}", e);
        }
    });

    Ok((
        StatusCode::ACCEPTED,
        Json(serde_json::json!({
            "deploymentId": id,
            "version": request.version,
            "state": "running"
        })),
    ))
}

/// 查询部署状态。
async fn status(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(query): Query<StatusQuery>,
) -> Result<Json<DeployStatus>, (StatusCode, String)> {
    check_auth(&headers, &state.deploy_token)?;

    if !is_valid_id(&query.id) {
        return Err((StatusCode::BAD_REQUEST, "invalid deployment id".to_string()));
    }

    let path = state.status_dir.join(format!("{}.json", query.id));

    let content = fs::read_to_string(&path)
        .map_err(|_| (StatusCode::NOT_FOUND, "deployment not found".to_string()))?;

    let status: DeployStatus = serde_json::from_str(&content).map_err(|e| {
        (
            StatusCode::INTERNAL_SERVER_ERROR,
            format!("invalid deploy state: {}", e),
        )
    })?;

    Ok(Json(status))
}

// =========================================================
// 校验
// =========================================================

fn check_auth(headers: &HeaderMap, token: &str) -> Result<(), (StatusCode, String)> {
    let authorization = headers
        .get("authorization")
        .and_then(|v| v.to_str().ok())
        .unwrap_or("");

    let expected = format!("Bearer {}", token);

    // 常量时间比较，防止时序攻击
    if bool::from(subtle::ConstantTimeEq::ct_eq(
        authorization.as_bytes(),
        expected.as_bytes(),
    )) {
        Ok(())
    } else {
        Err((StatusCode::UNAUTHORIZED, "invalid authorization".to_string()))
    }
}

fn is_valid_version(version: &str) -> bool {
    let version = version.strip_prefix('v').unwrap_or(version);

    let parts: Vec<&str> = version.split('.').collect();

    if parts.len() != 3 {
        return false;
    }

    parts[0].parse::<u64>().is_ok()
        && parts[1].parse::<u64>().is_ok()
        && parts[2]
            .split('-')
            .next()
            .unwrap_or("")
            .parse::<u64>()
            .is_ok()
}

fn is_valid_digest(digest: &str) -> bool {
    let Some(hash) = digest.strip_prefix("sha256:") else {
        return false;
    };

    hash.len() == 64 && hash.chars().all(|c| c.is_ascii_hexdigit())
}

/// deploymentId 会拼接为文件名，只允许安全字符，防止路径穿越
fn is_valid_id(id: &str) -> bool {
    !id.is_empty()
        && id.len() <= 128
        && id
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, '-' | '.' | '_'))
}

// =========================================================
// 状态持久化
// =========================================================

fn now_millis() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}

/// 原子写入：先写临时文件再 rename，避免轮询方读到半截内容
fn write_status(dir: &Path, status: &DeployStatus) -> std::io::Result<()> {
    fs::create_dir_all(dir)?;

    let final_path = dir.join(format!("{}.json", status.id));
    let tmp_path = dir.join(format!(".{}.tmp", status.id));

    {
        let mut file = fs::File::create(&tmp_path)?;
        serde_json::to_writer(&mut file, status)
            .map_err(|e| std::io::Error::other(e.to_string()))?;
        file.sync_all()?;
    }

    fs::rename(&tmp_path, &final_path)?;
    Ok(())
}

/// 扫描状态目录，返回正在运行的部署（及其文件修改时间）
fn running_deploy(dir: &Path) -> std::io::Result<Option<(PathBuf, DeployStatus, SystemTime)>> {
    let entries = match fs::read_dir(dir) {
        Ok(entries) => entries,
        Err(_) => return Ok(None),
    };

    for entry in entries.flatten() {
        let path = entry.path();

        if path.extension().and_then(|e| e.to_str()) != Some("json") {
            continue;
        }

        let content = match fs::read_to_string(&path) {
            Ok(content) => content,
            Err(_) => continue,
        };

        let status: DeployStatus = match serde_json::from_str(&content) {
            Ok(status) => status,
            Err(_) => continue,
        };

        if status.state == "running" {
            let mtime = entry
                .metadata()
                .and_then(|m| m.modified())
                .unwrap_or(UNIX_EPOCH);
            return Ok(Some((path, status, mtime)));
        }
    }

    Ok(None)
}

/// 清理超过保留时长的历史状态文件
fn cleanup_old_status(dir: &Path, retain: Duration) {
    let Ok(entries) = fs::read_dir(dir) else {
        return;
    };

    let Some(cutoff) = SystemTime::now().checked_sub(retain) else {
        return;
    };

    for entry in entries.flatten() {
        let path = entry.path();

        if path.extension().and_then(|e| e.to_str()) != Some("json") {
            continue;
        }

        let Ok(mtime) = entry.metadata().and_then(|m| m.modified()) else {
            continue;
        };

        if mtime < cutoff && fs::remove_file(&path).is_ok() {
            info!(path = %path.display(), "removed old deploy status");
        }
    }
}

/// 读取部署日志末尾；只截取最后一次 "Deployment started" 之后的内容，
/// 避免共享日志里混入上一次部署的输出。
fn tail_log(path: &Path, lines: usize) -> String {
    let Ok(content) = fs::read_to_string(path) else {
        return String::new();
    };

    let start = content.rfind("Deployment started").unwrap_or(0);
    content[start..]
        .lines()
        .rev()
        .take(lines)
        .collect::<Vec<_>>()
        .into_iter()
        .rev()
        .collect::<Vec<_>>()
        .join("\n")
}
