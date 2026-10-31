package com.smartwash.game.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.exception.GameServiceException;
import com.smartwash.game.entity.GameConfig;
import com.smartwash.game.from.SearchGameFrom;
import com.smartwash.game.from.UpdateGameFrom;
import com.smartwash.game.from.UploadGameFrom;
import com.smartwash.game.mapper.GameConfigMapper;
import com.smartwash.game.storage.GameObjectStorage;
import com.smartwash.game.support.GamePackageValidator;
import com.smartwash.game.vo.AdminGameVo;
import com.smartwash.game.vo.GameVo;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 游戏库服务测试。
 * 覆盖：上传链路（扩展名/大小/zip 炸弹/ZipSlip/单 html/覆盖更新/409）、CAS 开关（成功/幂等/404）、
 * 物理删除（fail-closed 先清存储、外链不清存储）、管理端分页筛选、用户端下发列表（XML 查询 + Vo 映射）。
 * 纯 Mockito 单测，baseMapper 与 self（缓存自注入）手动注入，存储层 mock GameObjectStorage，不校验缓存注解本身。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GameConfigServiceImpl 上传/开关/删除/下发测试")
class GameConfigServiceImplTest {

    @Mock
    private GameConfigMapper gameMapper;

    @Mock
    private GameObjectStorage objectStorage;

    private GameConfigServiceImpl service;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void initTableInfo() {
        // 纯单测环境无 MyBatis 容器：手动初始化实体 TableInfo，供 lambda 列名解析与 wrapper SQL 段物化
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, GameConfig.class);
    }

    @BeforeEach
    void setUp() {
        service = new GameConfigServiceImpl(new GamePackageValidator(), objectStorage);
        ReflectionTestUtils.setField(service, "baseMapper", gameMapper);
        ReflectionTestUtils.setField(service, "publicBaseUrl", "https://demo.smartwash.cn");
        ReflectionTestUtils.setField(service, "self", service);
    }

    // ==================== 工具 ====================

    private UploadGameFrom uploadFrom(String gameKey) {
        UploadGameFrom from = new UploadGameFrom();
        from.setGameKey(gameKey);
        from.setTitle("贪吃蛇");
        from.setDescription("滑动转向");
        from.setIcon("gamepad");
        from.setColor("teal");
        from.setCategory("arcade");
        from.setSort(3);
        return from;
    }

    private MockMultipartFile htmlFile() {
        return new MockMultipartFile("file", "snake.html", "text/html",
                "<html><body>snake</body></html>".getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile zipFile(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return new MockMultipartFile("file", "snake.zip", "application/zip", bos.toByteArray());
    }

    private GameConfig record(Long id, String gameKey, Integer enabled, Integer sourceType) {
        return new GameConfig()
                .setId(id)
                .setGameKey(gameKey)
                .setTitle("游戏" + id)
                .setIcon("gamepad")
                .setColor("teal")
                .setCategory("arcade")
                .setSourceType(sourceType)
                .setObjectKey(sourceType == 2 ? null : "game/" + gameKey + "/")
                .setUrl("https://demo.smartwash.cn/web/game/" + gameKey + "/")
                .setEnabled(enabled)
                .setSort(1)
                .setUpdatedAt(LocalDateTime.of(2026, 10, 31, 12, 0));
    }

    // ==================== 上传：单 html ====================

    @Test
    @DisplayName("上传单 html：落为 index.html 对象，字段完整映射，默认不下发（enabled=0）")
    void upload_singleHtml_mapsFieldsAndStores() {
        when(gameMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(gameMapper.insert(any(GameConfig.class))).thenAnswer(inv -> {
            inv.getArgument(0, GameConfig.class).setId(7L);
            return 1;
        });
        when(gameMapper.selectById(7L)).thenReturn(record(7L, "snake", 0, 1));

        AdminGameVo vo = service.upload(uploadFrom("snake"), htmlFile());

        // 对象写入：键固定 game/snake/index.html，Content-Type 为 text/html
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> mimeCaptor = ArgumentCaptor.forClass(String.class);
        verify(objectStorage).putObject(keyCaptor.capture(), any(byte[].class), mimeCaptor.capture());
        assertEquals("game/snake/index.html", keyCaptor.getValue());
        assertTrue(mimeCaptor.getValue().startsWith("text/html"));
        // 覆盖前清理旧前缀（幂等，对象不存在时由存储层静默）
        verify(objectStorage).removeObjectsByPrefix("game/snake/");

        ArgumentCaptor<GameConfig> savedCaptor = ArgumentCaptor.forClass(GameConfig.class);
        verify(gameMapper).insert(savedCaptor.capture());
        GameConfig saved = savedCaptor.getValue();
        assertEquals("snake", saved.getGameKey());
        assertEquals("贪吃蛇", saved.getTitle());
        assertEquals("index.html", saved.getEntryPath());
        assertEquals("game/snake/", saved.getObjectKey());
        assertEquals("https://demo.smartwash.cn/web/game/snake/", saved.getUrl());
        assertEquals(0, saved.getEnabled(), "上传后默认不下发，上架由开关接口显式选择");
        assertEquals(3, saved.getSort());
        assertEquals(1, saved.getSourceType());

        assertEquals("https://demo.smartwash.cn/web/game/snake/", vo.getUrl());
    }

    @Test
    @DisplayName("上传单 html：同 gameKey 重复上传 = 覆盖更新（updateById 而非 insert），URL 不变")
    void upload_duplicateGameKey_overwrites() {
        GameConfig existing = record(7L, "snake", 1, 1);
        when(gameMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(gameMapper.updateById(any(GameConfig.class))).thenReturn(1);
        when(gameMapper.selectById(7L)).thenReturn(existing);

        service.upload(uploadFrom("snake"), htmlFile());

        verify(gameMapper, never()).insert(any(GameConfig.class));
        verify(gameMapper).updateById(any(GameConfig.class));
        verify(objectStorage).removeObjectsByPrefix("game/snake/");
    }

    @Test
    @DisplayName("上传单 html：并发窗口唯一索引冲突转 409 信封码")
    void upload_duplicateKeyRace_conflict409() {
        when(gameMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(gameMapper.insert(any(GameConfig.class))).thenThrow(new DuplicateKeyException("uk_game_key"));

        GameServiceException ex = assertThrows(GameServiceException.class,
                () -> service.upload(uploadFrom("snake"), htmlFile()));
        assertEquals(GameServiceException.CONFLICT, ex.getCode());
    }

    // ==================== 上传：zip 包 ====================

    @Test
    @DisplayName("上传 zip：多文件解压入库，入口探测 index.html，file_size 记解压总字节")
    void upload_zipPackage_extractsAndUploadsAll() throws Exception {
        MockMultipartFile zip = zipFile(Map.of(
                "index.html", "<html>2048</html>".getBytes(StandardCharsets.UTF_8),
                "assets/main.js", "console.log(1)".getBytes(StandardCharsets.UTF_8)));
        when(gameMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(gameMapper.insert(any(GameConfig.class))).thenAnswer(inv -> {
            inv.getArgument(0, GameConfig.class).setId(8L);
            return 1;
        });
        when(gameMapper.selectById(8L)).thenReturn(record(8L, "g2048", 0, 1));

        service.upload(uploadFrom("g2048"), zip);

        // 解压出的 2 个文件都必须写入对象存储，键为相对路径
        verify(objectStorage).putObject(eq("game/g2048/index.html"), any(byte[].class), startsWith("text/html"));
        verify(objectStorage).putObject(eq("game/g2048/assets/main.js"), any(byte[].class), startsWith("application/javascript"));
        ArgumentCaptor<GameConfig> savedCaptor = ArgumentCaptor.forClass(GameConfig.class);
        verify(gameMapper).insert(savedCaptor.capture());
        assertEquals("index.html", savedCaptor.getValue().getEntryPath());
        assertTrue(savedCaptor.getValue().getFileSize() > 0, "file_size 必须记录解压后总字节");
    }

    @Test
    @DisplayName("上传 zip：根目录缺 html 入口拒绝（400），存储零写入")
    void upload_zipWithoutEntry_rejected() throws Exception {
        MockMultipartFile zip = zipFile(Map.of("readme.txt", "no html".getBytes(StandardCharsets.UTF_8)));

        GameServiceException ex = assertThrows(GameServiceException.class,
                () -> service.upload(uploadFrom("snake"), zip));
        assertEquals(GameServiceException.BAD_REQUEST, ex.getCode());
        verify(objectStorage, never()).putObject(anyString(), any(byte[].class), anyString());
    }

    @Test
    @DisplayName("上传 zip：ZipSlip 穿越包拒绝（403），旧前缀与记录零触碰")
    void upload_zipSlip_rejectedBeforeAnyWrite() throws Exception {
        MockMultipartFile zip = zipFile(Map.of("../evil.html", "<html>".getBytes(StandardCharsets.UTF_8)));

        GameServiceException ex = assertThrows(GameServiceException.class,
                () -> service.upload(uploadFrom("snake"), zip));
        assertEquals(GameServiceException.FORBIDDEN, ex.getCode());
        verify(objectStorage, never()).removeObjectsByPrefix(anyString());
        verify(objectStorage, never()).putObject(anyString(), any(byte[].class), anyString());
        verify(gameMapper, never()).insert(any(GameConfig.class));
    }

    @Test
    @DisplayName("上传 zip：解压成功后才清理旧前缀——解压失败时旧游戏对象保持完整")
    void upload_zipExtractionFails_oldPrefixUntouched() throws Exception {
        // 解压总字节超 20MB 的炸弹：extractZip 抛 413
        MockMultipartFile zip = zipFile(Map.of("bomb.txt", new byte[(int) (GamePackageValidator.MAX_TOTAL_UNCOMPRESSED + 1024)]));

        assertThrows(GameServiceException.class, () -> service.upload(uploadFrom("snake"), zip));
        verify(objectStorage, never()).removeObjectsByPrefix(anyString());
    }

    // ==================== CAS 开关 ====================

    @Test
    @DisplayName("开关：CAS 条件更新影响 1 行 → 成功，不再回查")
    void changeEnabled_casUpdateApplied() {
        when(gameMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        service.changeEnabled(1L, 1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<GameConfig>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(gameMapper).update(isNull(), captor.capture());
        captor.getValue().getSqlSegment(); // 物化 SQL 段，条件参数写入 paramNameValuePairs
        var params = captor.getValue().getParamNameValuePairs();
        assertTrue(params.containsValue(1L), "条件必须包含 id");
        assertTrue(params.containsValue(1), "条件必须包含 enabled <> 目标值");
    }

    @Test
    @DisplayName("开关：CAS 影响 0 行且记录不存在 → 404")
    void changeEnabled_zeroRowsAndMissing_notFound() {
        when(gameMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
        when(gameMapper.selectById(404L)).thenReturn(null);

        GameServiceException ex = assertThrows(GameServiceException.class,
                () -> service.changeEnabled(404L, 1));
        assertEquals(GameServiceException.NOT_FOUND, ex.getCode());
    }

    @Test
    @DisplayName("开关：CAS 影响 0 行但记录存在（已是目标状态）→ 幂等成功")
    void changeEnabled_zeroRowsButExists_idempotent() {
        when(gameMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
        when(gameMapper.selectById(1L)).thenReturn(record(1L, "snake", 1, 1));

        service.changeEnabled(1L, 1);
        verify(gameMapper).update(isNull(), any(Wrapper.class));
    }

    // ==================== 物理删除 ====================

    @Test
    @DisplayName("删除：不存在 → 404，存储与记录零触碰")
    void deleteGame_missing_notFound() {
        when(gameMapper.selectById(404L)).thenReturn(null);

        assertThrows(GameServiceException.class, () -> service.deleteGame(404L));
        verify(objectStorage, never()).removeObjectsByPrefix(anyString());
        verify(gameMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除：上传包先清 MinIO 前缀（fail-closed）再删记录，顺序可重试")
    void deleteGame_package_removesPrefixBeforeRecord() {
        when(gameMapper.selectById(1L)).thenReturn(record(1L, "snake", 1, 1));
        when(gameMapper.deleteById(1L)).thenReturn(1);

        service.deleteGame(1L);

        InOrder ordered = inOrder(objectStorage, gameMapper);
        ordered.verify(objectStorage).removeObjectsByPrefix("game/snake/");
        ordered.verify(gameMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除：存储清理失败阻断删除（记录保留可重试），不产生无主对象")
    void deleteGame_storageFailure_blocksDelete() {
        when(gameMapper.selectById(1L)).thenReturn(record(1L, "snake", 1, 1));
        doThrow(new GameServiceException(GameServiceException.BAD_REQUEST, "清理失败"))
                .when(objectStorage).removeObjectsByPrefix("game/snake/");

        assertThrows(GameServiceException.class, () -> service.deleteGame(1L));
        verify(gameMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除：外链记录（source_type=2）不清存储直接删记录")
    void deleteGame_external_skipsStorage() {
        when(gameMapper.selectById(2L)).thenReturn(record(2L, "external-game", 1, 2));
        when(gameMapper.deleteById(2L)).thenReturn(1);

        service.deleteGame(2L);

        verify(objectStorage, never()).removeObjectsByPrefix(anyString());
        verify(gameMapper).deleteById(2L);
    }

    // ==================== 元数据编辑 ====================

    @Test
    @DisplayName("编辑：不存在拒绝（404）；gameKey 永不修改；空串清空 badge")
    void updateGame_partialFields_appliedAndKeyUntouched() {
        GameConfig existing = record(1L, "snake", 1, 1).setBadge("热").setRemark("运营位");
        when(gameMapper.selectById(1L)).thenReturn(existing);
        when(gameMapper.updateById(any(GameConfig.class))).thenReturn(1);

        UpdateGameFrom from = new UpdateGameFrom();
        from.setTitle("贪吃蛇Pro");
        from.setBadge("");
        service.updateGame(1L, from);

        ArgumentCaptor<GameConfig> captor = ArgumentCaptor.forClass(GameConfig.class);
        verify(gameMapper).updateById(captor.capture());
        GameConfig updated = captor.getValue();
        assertEquals("贪吃蛇Pro", updated.getTitle());
        assertNull(updated.getBadge(), "badge 空串必须清空为 NULL");
        assertEquals("运营位", updated.getRemark(), "未传字段不得被修改");
        assertEquals("snake", updated.getGameKey(), "gameKey 是端上与 URL 对齐标识，不可修改");
    }

    // ==================== 管理端分页 ====================

    @Test
    @DisplayName("管理端分页：keyword 同时模糊 title/gameKey，category/enabled 精确，sort/id 升序")
    void adminPage_filtersApplied() {
        Page<GameConfig> result = new Page<>(1, 10, 0);
        result.setRecords(List.of());
        doReturn(result).when(gameMapper).selectPage(any(), any());

        SearchGameFrom from = new SearchGameFrom();
        from.setKeyword("snake");
        from.setCategory("arcade");
        from.setEnabled(0);
        service.adminPage(from);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<GameConfig>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(gameMapper).selectPage(any(), captor.capture());
        captor.getValue().getSqlSegment();
        var params = captor.getValue().getParamNameValuePairs();
        assertTrue(params.values().stream().anyMatch(v -> v instanceof String s && s.contains("snake")),
                "keyword 模糊筛选必须进入查询条件");
        assertTrue(params.containsValue("arcade"), "category 筛选必须进入查询条件");
        assertTrue(params.containsValue(0), "enabled 筛选必须进入查询条件");

        String segment = captor.getValue().getSqlSegment().toUpperCase();
        int orderByIdx = segment.indexOf("ORDER BY");
        assertTrue(orderByIdx >= 0, "查询必须带 ORDER BY 段");
        String orderPart = captor.getValue().getSqlSegment().substring(orderByIdx);
        assertTrue(orderPart.indexOf("sort") < orderPart.indexOf("id"), "必须先按 sort 再按 id 排序");
        assertTrue(!orderPart.toUpperCase().contains("DESC"), "必须为升序排序");
    }

    @Test
    @DisplayName("管理端分页：Vo 含管理字段（enabled/sourceType/entryPath/remark）")
    void adminPage_mapsAdminFields() {
        GameConfig entry = record(1L, "snake", 0, 1).setEntryPath("index.html").setRemark("运营位");
        Page<GameConfig> result = new Page<>(1, 10, 1);
        result.setRecords(List.of(entry));
        doReturn(result).when(gameMapper).selectPage(any(), any());

        Page<AdminGameVo> page = service.adminPage(new SearchGameFrom());

        AdminGameVo vo = page.getRecords().get(0);
        assertEquals(1L, vo.getId());
        assertEquals(0, vo.getEnabled());
        assertEquals(1, vo.getSourceType());
        assertEquals("index.html", vo.getEntryPath());
        assertEquals("运营位", vo.getRemark());
    }

    // ==================== 用户端下发列表 ====================

    @Test
    @DisplayName("用户端列表：走 XML 下发查询（enabled=1/sort 排序由 SQL 保证），仅映射用户端字段")
    void listEnabledGames_mapsUserFacingFieldsOnly() {
        when(gameMapper.selectEnabledList()).thenReturn(List.of(
                record(2L, "g2048", 1, 1).setTitle("2048").setBadge("新"),
                record(1L, "snake", 1, 1).setTitle("贪吃蛇")));

        List<GameVo> voList = service.listEnabledGames();

        verify(gameMapper).selectEnabledList();
        assertEquals(2, voList.size());
        GameVo vo = voList.get(0);
        assertEquals("g2048", vo.getGameKey());
        assertEquals("2048", vo.getTitle());
        assertEquals("新", vo.getBadge());
        assertEquals("https://demo.smartwash.cn/web/game/g2048/", vo.getUrl());
        assertEquals("arcade", vo.getCategory());
        // Vo 类型层面不含管理字段，这里确认 url/游戏标识等用户端关键字段完整
        assertTrue(vo.getGameKey() != null && vo.getUrl() != null);
    }
}
