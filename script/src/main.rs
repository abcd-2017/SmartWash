use axum::{
    extract::State,
    http::{HeaderMap, StatusCode},
    routing::post,
    Json, Router,
};
use serde::Deserialize;
use std::{
    env,
    process::Stdio,
    sync::Arc,
    time::Duration,
};
use tokio::process::Command;
use tracing::{error, info, warn};

const DEPLOY_LOG: &str = "/opt/smartwash/deploy.log";

#[derive(Clone)]
struct AppState {
    deploy_token: Arc<String>,
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

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();

    let deploy_token = env::var("DEPLOY_TOKEN")
        .expect("DEPLOY_TOKEN environment variable is required");

    let state = AppState {
        deploy_token: Arc::new(deploy_token),
    };

    let app = Router::new()
        .route("/api/deploy", post(deploy))
        .with_state(state);

    let addr = "0.0.0.0:18080";

    info!("Deploy service listening on {}", addr);

    let listener = tokio::net::TcpListener::bind(addr)
        .await
        .expect("failed to bind");

    axum::serve(listener, app)
        .await
        .expect("server error");
}

async fn deploy(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(request): Json<DeployRequest>,
) -> Result<Json<serde_json::Value>, (StatusCode, String)> {

    // =====================================================
    // 1. 验证 Token
    // =====================================================

    let authorization = headers
        .get("authorization")
        .and_then(|v| v.to_str().ok())
        .unwrap_or("");

    let expected = format!("Bearer {}", state.deploy_token);

    // 常量时间比较，防止时序攻击
    if !bool::from(subtle::ConstantTimeEq::ct_eq(
        authorization.as_bytes(),
        expected.as_bytes(),
    )) {
        return Err((
            StatusCode::UNAUTHORIZED,
            "invalid authorization".to_string(),
        ));
    }

    // =====================================================
    // 2. 校验 version
    // =====================================================

    if !is_valid_version(&request.version) {
        return Err((
            StatusCode::BAD_REQUEST,
            "invalid version".to_string(),
        ));
    }

    // =====================================================
    // 3. 校验 digest
    // =====================================================

    if !is_valid_digest(&request.backend_digest)
        || !is_valid_digest(&request.web_digest)
    {
        return Err((
            StatusCode::BAD_REQUEST,
            "invalid image digest".to_string(),
        ));
    }

    // =====================================================
    // 4. 构建完整镜像地址
    // =====================================================

    let backend_image = format!(
        "{}@{}",
        request.backend_image,
        request.backend_digest
    );

    let web_image = format!(
        "{}@{}",
        request.web_image,
        request.web_digest
    );

    info!(
        version = %request.version,
        backend = %backend_image,
        web = %web_image,
        "deployment requested"
    );

    // =====================================================
    // 5. 调用固定 deploy.sh
    //
    // 注意：
    // 不允许请求传入 shell command。
    //
    // =====================================================

    let output = tokio::time::timeout(
        Duration::from_secs(300),
        Command::new("/opt/smartwash/deploy.sh")
            .arg(&request.version)
            .arg(&backend_image)
            .arg(&web_image)
            .stdin(Stdio::null())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .output(),
    )
    .await
    .map_err(|_| {
        error!(version = %request.version, "deploy.sh timed out");
        (
            StatusCode::GATEWAY_TIMEOUT,
            "deployment timed out".to_string(),
        )
    })?
    .map_err(|e| {
        error!("failed to execute deploy.sh: {}", e);
        (
            StatusCode::INTERNAL_SERVER_ERROR,
            "failed to start deploy".to_string(),
        )
    })?;

    // -----------------------------------------------------
    // 增强日志：同时输出 deploy.sh 的 stdout/stderr 和 deploy.log 末尾
    // 因为 deploy.sh 使用 `exec >> $LOG_FILE 2>&1` 重定向，
    // piped() 捕获的输出可能为空，需要从日志文件补充诊断信息
    // -----------------------------------------------------

    let stdout_str = String::from_utf8_lossy(&output.stdout).trim().to_string();
    let stderr_str = String::from_utf8_lossy(&output.stderr).trim().to_string();

    // 始终记录 exit code，无论成功失败
    info!(
        version = %request.version,
        exit_code = ?output.status.code(),
        stdout_len = stdout_str.len(),
        stderr_len = stderr_str.len(),
        "deploy.sh finished"
    );

    // 如果 piped 输出为空，尝试从 deploy.log 读取末尾作为补充诊断
    if stdout_str.is_empty() && stderr_str.is_empty() {
        match tokio::fs::read_to_string(DEPLOY_LOG).await {
            Ok(log_content) => {
                let tail: String = log_content
                    .lines()
                    .rev()
                    .take(30)
                    .collect::<Vec<_>>()
                    .into_iter()
                    .rev()
                    .collect::<Vec<_>>()
                    .join("\n");
                warn!(
                    version = %request.version,
                    "deploy.sh produced no piped output, showing last 30 lines of {}:\n{}",
                    DEPLOY_LOG,
                    tail
                );
            }
            Err(e) => {
                warn!(
                    version = %request.version,
                    "deploy.sh produced no output and could not read {}: {}",
                    DEPLOY_LOG,
                    e
                );
            }
        }
    }

    if !output.status.success() {
        let exit_info = match output.status.code() {
            Some(code) => format!("exit code {}", code),
            None => "terminated by signal".to_string(),
        };

        // 构建详细的错误日志
        let mut error_details = format!("deployment failed ({})", exit_info);

        if !stderr_str.is_empty() {
            error_details.push_str(&format!("\nstderr:\n{}", stderr_str));
        }
        if !stdout_str.is_empty() {
            error_details.push_str(&format!("\nstdout:\n{}", stdout_str));
        }

        // 尝试读取 deploy.log 末尾，定位具体失败步骤
        if let Ok(log_content) = tokio::fs::read_to_string(DEPLOY_LOG).await {
            let tail: String = log_content
                .lines()
                .rev()
                .take(50)
                .collect::<Vec<_>>()
                .into_iter()
                .rev()
                .collect::<Vec<_>>()
                .join("\n");
            error_details.push_str(&format!("\n\nLast 50 lines of {}:\n{}", DEPLOY_LOG, tail));
        }

        error!(version = %request.version, "{}", error_details);

        // 对外返回简要信息（避免泄露服务器内部路径等敏感信息）
        let mut client_message = format!("deployment failed ({})", exit_info);
        if !stderr_str.is_empty() {
            // 只返回 stderr 的前 500 字符，避免响应过大
            let truncated = if stderr_str.len() > 500 {
                &stderr_str[..500]
            } else {
                &stderr_str
            };
            client_message.push_str(": ");
            client_message.push_str(truncated);
        }

        return Err((
            StatusCode::INTERNAL_SERVER_ERROR,
            client_message,
        ));
    }

    // 成功时也记录 stdout（如果 deploy.sh 改为 tee 重定向后这里会有内容）
    if !stdout_str.is_empty() {
        info!(
            version = %request.version,
            "deployment output:\n{}",
            stdout_str
        );
    }

    Ok(Json(serde_json::json!({
        "success": true,
        "version": request.version
    })))
}


// =========================================================
// VERSION 校验
// =========================================================

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


// =========================================================
// Digest 校验
// =========================================================

fn is_valid_digest(digest: &str) -> bool {
    let Some(hash) = digest.strip_prefix("sha256:") else {
        return false;
    };

    hash.len() == 64
        && hash.chars().all(|c| c.is_ascii_hexdigit())
}
