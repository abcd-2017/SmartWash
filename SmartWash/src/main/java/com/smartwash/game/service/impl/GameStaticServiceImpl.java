package com.smartwash.game.service.impl;

import cn.hutool.core.util.StrUtil;
import com.smartwash.exception.GameServiceException;
import com.smartwash.game.entity.GameConfig;
import com.smartwash.game.mapper.GameConfigMapper;
import com.smartwash.game.service.GameStaticService;
import com.smartwash.game.storage.GameObjectStorage;
import com.smartwash.game.support.GameContentTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;

/**
 * 游戏静态转发支撑实现。
 * 安全基线：
 * - 相对路径手工分段规范化：忽略空段与 "."，遇 ".."、反斜杠、前导斜杠一律拒绝（403，路径穿越不伪装成 404）；
 *   解码用 UriUtils（Tomcat 默认已拒绝 encoded slash，此处再兜 %2e%2e 等编码变体）；
 * - 对象键 = 记录 object_key 前缀 + 规范化相对路径，天然落在前缀内；
 * - 停用（enabled=0）/未知 gameKey/外链（无 object_key）统一 404 语义，不泄露存在性。
 * ETag 取 gameKey+updated_at+相对路径 摘要：同资源重传后 updated_at 变化 → 缓存自动失效；
 * 未变化的资源（如 css）etag 稳定 → WebView 304。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameStaticServiceImpl implements GameStaticService {

    /** 来源类型：1上传包（外链记录不在本控制器转发范围） */
    private static final int SOURCE_TYPE_PACKAGE = 1;
    /** 下发状态：1启用 */
    private static final int ENABLED_TRUE = 1;
    /** 默认入口相对路径 */
    private static final String DEFAULT_ENTRY_PATH = "index.html";
    /** gameKey 合法格式（与 UploadGameFrom 约束一致；同时是对象键与 URL 的注入防线） */
    private static final String GAME_KEY_PATTERN = "^[a-z][a-z0-9_-]{2,63}$";

    private final GameConfigMapper gameConfigMapper;
    private final GameObjectStorage objectStorage;

    @Override
    public ResolvedObject resolve(String gameKey, String rawRelPath) {
        if (gameKey == null || !gameKey.matches(GAME_KEY_PATTERN)) {
            return null;
        }
        GameConfig config = gameConfigMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GameConfig>()
                        .eq(GameConfig::getGameKey, gameKey));
        if (config == null || config.getEnabled() == null || config.getEnabled() != ENABLED_TRUE) {
            // 未知 key 与停用游戏统一 404 语义
            return null;
        }
        if (config.getSourceType() == null || config.getSourceType() != SOURCE_TYPE_PACKAGE
                || StrUtil.isBlank(config.getObjectKey())) {
            // 外链记录不走本转发（url 已是外部地址）
            return null;
        }

        String relPath = normalizeRelativePath(decode(rawRelPath, config), config);
        String objectKey = config.getObjectKey() + relPath;
        GameObjectStorage.GameObject object = objectStorage.getObject(objectKey);
        if (object == null) {
            log.debug("游戏对象未命中, gameKey: {}, path: {}", gameKey, relPath);
            return null;
        }
        return new ResolvedObject(new GameStaticStream(object.stream()),
                GameContentTypes.forFilename(relPath),
                buildEtag(gameKey, config.getUpdatedAt(), relPath),
                object.contentLength());
    }

    /** URL 解码（非法编码按原样处理）； Tomcat 层拒绝 encoded slash 后此处兜剩余变体 */
    private String decode(String rawRelPath, GameConfig config) {
        if (StrUtil.isBlank(rawRelPath) || "/".equals(rawRelPath)) {
            return StrUtil.blankToDefault(config.getEntryPath(), DEFAULT_ENTRY_PATH);
        }
        try {
            return UriUtils.decode(rawRelPath, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new GameServiceException(GameServiceException.BAD_REQUEST, "资源路径编码非法");
        }
    }

    /**
     * 相对路径规范化：分段过滤空段与 "."，拒绝 ".." 与反斜杠（ZipSlip 同款思路，转发侧前缀内校验）。
     * 前导斜杠是 URL 结构本身，交由空段过滤消化；对象键 = 记录前缀 + 规范化结果，天然落在前缀内。
     * 返回规范化路径（如 "assets/js/main.js"）。
     */
    private String normalizeRelativePath(String decodedPath, GameConfig config) {
        if (decodedPath.indexOf('\\') >= 0) {
            throw new GameServiceException(GameServiceException.FORBIDDEN, "资源路径非法");
        }
        StringBuilder normalized = new StringBuilder();
        for (String segment : decodedPath.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                throw new GameServiceException(GameServiceException.FORBIDDEN, "资源路径非法");
            }
            if (normalized.length() > 0) {
                normalized.append('/');
            }
            normalized.append(segment);
        }
        if (normalized.length() == 0) {
            // 纯 "."/空段 请求 → 回入口
            return StrUtil.blankToDefault(config.getEntryPath(), DEFAULT_ENTRY_PATH);
        }
        return normalized.toString();
    }

    /** ETag：md5(gameKey|updated_at epoch|相对路径)，带引号（HTTP 语义） */
    private String buildEtag(String gameKey, LocalDateTime updatedAt, String relPath) {
        String updatedAtEpoch = updatedAt == null ? "0"
                : String.valueOf(updatedAt.atZone(ZoneId.systemDefault()).toEpochSecond());
        String source = gameKey + "|" + updatedAtEpoch + "|" + relPath;
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return "\"" + HexFormat.of().formatHex(digest) + "\"";
        } catch (Exception e) {
            // MD5 必可用；极端兜底退化为时间戳弱校验
            return "\"" + updatedAtEpoch + "\"";
        }
    }
}
