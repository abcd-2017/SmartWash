package com.smartwash.game.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smartwash.exception.GameServiceException;
import com.smartwash.game.entity.GameConfig;
import com.smartwash.game.mapper.GameConfigMapper;
import com.smartwash.game.service.GameStaticService;
import com.smartwash.game.storage.GameObjectStorage;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 游戏静态转发支撑服务测试。
 * 覆盖：Content-Type 按扩展名映射、未知/停用/外链/对象缺失统一 404（null）、
 * 路径穿越（../ 与反斜杠与编码变体）拒绝 403、入口回退（空路径→entry_path）、ETag 形态。
 * 纯 Mockito：mapper 与存储层全部 mock，不连 MinIO/MySQL。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GameStaticServiceImpl 转发定位与安全校验测试")
class GameStaticServiceImplTest {

    private static final String OBJECT_KEY = "game/snake/";

    @Mock
    private GameConfigMapper gameMapper;

    @Mock
    private GameObjectStorage objectStorage;

    private GameStaticServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, GameConfig.class);
    }

    @BeforeEach
    void setUp() {
        service = new GameStaticServiceImpl(gameMapper, objectStorage);
    }

    private GameConfig enabledSnake() {
        return new GameConfig()
                .setId(1L)
                .setGameKey("snake")
                .setSourceType(1)
                .setEntryPath("index.html")
                .setObjectKey(OBJECT_KEY)
                .setUrl("https://demo.smartwash.cn/web/game/snake/")
                .setEnabled(1)
                .setUpdatedAt(LocalDateTime.of(2026, 10, 31, 12, 0));
    }

    private GameObjectStorage.GameObject object(String content) {
        return new GameObjectStorage.GameObject(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), content.length(), "abc123");
    }

    private void stubSnake(GameConfig config) {
        when(gameMapper.selectOne(any(Wrapper.class))).thenReturn(config);
    }

    // ==================== 404 语义 ====================

    @Test
    @DisplayName("未知 gameKey（格式非法）直接 404，不触库")
    void resolve_illegalGameKey_null() {
        assertNull(service.resolve("../evil", ""));
        assertNull(service.resolve("UPPER", ""));
        assertNull(service.resolve("a b", ""));
        org.mockito.Mockito.verifyNoInteractions(gameMapper);
    }

    @Test
    @DisplayName("未知 gameKey（合法格式）与停用（enabled=0）统一 404 语义")
    void resolve_missingOrDisabled_null() {
        when(gameMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertNull(service.resolve("snake", ""));

        GameConfig disabled = enabledSnake().setEnabled(0);
        stubSnake(disabled);
        assertNull(service.resolve("snake", ""), "停用游戏必须 404，不得泄露内容");
        verify(objectStorage, org.mockito.Mockito.never()).getObject(anyString());
    }

    @Test
    @DisplayName("外链记录（source_type=2 / 无 objectKey）不走转发，404")
    void resolve_externalSource_null() {
        stubSnake(enabledSnake().setSourceType(2).setObjectKey(null));
        assertNull(service.resolve("snake", ""));
    }

    @Test
    @DisplayName("对象未命中（对象已删/键错）404")
    void resolve_objectMissing_null() {
        stubSnake(enabledSnake());
        when(objectStorage.getObject(OBJECT_KEY + "index.html")).thenReturn(null);
        assertNull(service.resolve("snake", ""));
    }

    // ==================== 正常命中与 Content-Type ====================

    @Test
    @DisplayName("空路径回退 entry_path（默认 index.html），Content-Type text/html;charset=utf-8")
    void resolve_emptyPath_fallsBackToEntryPath() {
        stubSnake(enabledSnake());
        when(objectStorage.getObject(OBJECT_KEY + "index.html")).thenReturn(object("<html/>"));

        GameStaticService.ResolvedObject resolved = service.resolve("snake", "");

        assertNotNull(resolved);
        assertEquals("text/html; charset=utf-8", resolved.contentType());
        assertEquals("<html/>".length(), resolved.contentLength());
        verify(objectStorage).getObject(OBJECT_KEY + "index.html");
    }

    @Test
    @DisplayName("相对路径拼接对象键前缀：/assets/main.js → game/snake/assets/main.js，Content-Type javascript")
    void resolve_relativePath_mappedContentType() {
        stubSnake(enabledSnake());
        when(objectStorage.getObject(OBJECT_KEY + "assets/main.js")).thenReturn(object("console.log(1)"));

        GameStaticService.ResolvedObject resolved = service.resolve("snake", "/assets/main.js");

        assertNotNull(resolved);
        assertEquals("application/javascript; charset=utf-8", resolved.contentType());
        verify(objectStorage).getObject(OBJECT_KEY + "assets/main.js");
    }

    @Test
    @DisplayName("Content-Type 映射抽查：css/png/svg/wasm/mp3/woff2/未知扩展名")
    void resolve_contentTypeMatrix() {
        stubSnake(enabledSnake());
        when(objectStorage.getObject(anyString())).thenReturn(object("x"));

        assertEquals("text/css; charset=utf-8", service.resolve("snake", "/s.css").contentType());
        assertEquals("image/png", service.resolve("snake", "/s.png").contentType());
        assertEquals("image/svg+xml", service.resolve("snake", "/s.svg").contentType());
        assertEquals("application/wasm", service.resolve("snake", "/s.wasm").contentType());
        assertEquals("audio/mpeg", service.resolve("snake", "/s.mp3").contentType());
        assertEquals("font/woff2", service.resolve("snake", "/s.woff2").contentType());
        assertEquals("application/octet-stream", service.resolve("snake", "/s.xyz").contentType());
    }

    @Test
    @DisplayName("ETag 带引号且随 updated_at/路径变化；同资源稳定")
    void resolve_etagShapeAndStability() {
        stubSnake(enabledSnake());
        when(objectStorage.getObject(OBJECT_KEY + "index.html")).thenReturn(object("<html/>"));

        String etag1 = service.resolve("snake", "").etag();
        String etag2 = service.resolve("snake", "").etag();
        String etagOther = service.resolve("snake", "/").etag();

        assertTrue(etag1.startsWith("\"") && etag1.endsWith("\""), "ETag 必须带引号（HTTP 语义）");
        assertEquals(etag1, etag2, "同资源同版本 ETag 稳定");
        assertEquals(etag1, etagOther, "空路径与 / 语义相同，ETag 一致");

        // 重传后 updated_at 变化 → ETag 变化 → WebView 缓存失效
        stubSnake(enabledSnake().setUpdatedAt(LocalDateTime.of(2026, 11, 1, 0, 0)));
        String etagAfterReupload = service.resolve("snake", "").etag();
        assertTrue(!etag1.equals(etagAfterReupload), "重传后 ETag 必须变化");
    }

    // ==================== 路径穿越（403） ====================

    @Test
    @DisplayName("路径穿越：../ 明文拒绝（403）")
    void resolve_dotDotTraversal_forbidden() {
        stubSnake(enabledSnake());

        GameServiceException ex = assertThrows(GameServiceException.class,
                () -> service.resolve("snake", "/../other/index.html"));
        assertEquals(GameServiceException.FORBIDDEN, ex.getCode());
    }

    @Test
    @DisplayName("路径穿越：%2e%2e 编码变体解码后拒绝（403）")
    void resolve_encodedTraversal_forbidden() {
        stubSnake(enabledSnake());

        GameServiceException ex = assertThrows(GameServiceException.class,
                () -> service.resolve("snake", "/%2e%2e/secret.txt"));
        assertEquals(GameServiceException.FORBIDDEN, ex.getCode());
    }

    @Test
    @DisplayName("路径穿越：反斜杠分隔（..\\）拒绝（403）")
    void resolve_backslashTraversal_forbidden() {
        stubSnake(enabledSnake());

        GameServiceException ex = assertThrows(GameServiceException.class,
                () -> service.resolve("snake", "/..\\secret.txt"));
        assertEquals(GameServiceException.FORBIDDEN, ex.getCode());
    }

    @Test
    @DisplayName("穿越段落居中（assets/../../etc）同样拒绝（403）")
    void resolve_midPathTraversal_forbidden() {
        stubSnake(enabledSnake());

        GameServiceException ex = assertThrows(GameServiceException.class,
                () -> service.resolve("snake", "/assets/../../etc/passwd"));
        assertEquals(GameServiceException.FORBIDDEN, ex.getCode());
    }

    // ==================== 对象键构造 ====================

    @Test
    @DisplayName("规范化路径消解多斜杠与 . 段：//assets/./a.js → assets/a.js")
    void resolve_normalizesSegments() {
        stubSnake(enabledSnake());
        when(objectStorage.getObject(anyString())).thenReturn(object("x"));

        service.resolve("snake", "//assets/./a.js");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(objectStorage).getObject(keyCaptor.capture());
        assertEquals(OBJECT_KEY + "assets/a.js", keyCaptor.getValue());
    }

    @Test
    @DisplayName("selectOne 按 gameKey 精确查询")
    void resolve_queriesByGameKey() {
        stubSnake(enabledSnake());
        when(objectStorage.getObject(anyString())).thenReturn(object("x"));

        service.resolve("snake", "/index.html");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<GameConfig>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(gameMapper).selectOne(captor.capture());
        var wrapper = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GameConfig>) captor.getValue();
        wrapper.getSqlSegment();
        assertTrue(wrapper.getParamNameValuePairs().containsValue("snake"),
                "gameKey 精确匹配必须进入查询条件");
        assertEquals(1, wrapper.getParamNameValuePairs().size());
    }
}
