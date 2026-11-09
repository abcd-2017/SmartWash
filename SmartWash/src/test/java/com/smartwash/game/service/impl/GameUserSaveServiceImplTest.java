package com.smartwash.game.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.game.entity.GameUserSave;
import com.smartwash.game.mapper.GameUserSaveMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 游戏账号存档服务测试。
 * 覆盖：读（有/无记录）、首写、覆盖写、非法 JSON 拒绝、空内容拒绝、超限拒绝、并发首写回退更新。
 * 纯 Mockito 单测，baseMapper 手动注入。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameUserSaveServiceImplTest {

    @Mock
    private GameUserSaveMapper mapper;

    private GameUserSaveServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // 纯单测环境无 MyBatis 容器：手动初始化实体 TableInfo，供 lambda 列名解析
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, GameUserSave.class);
    }

    @BeforeEach
    void setUp() {
        service = new GameUserSaveServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    private GameUserSave existing(Long userId, String gameKey, String json) {
        return new GameUserSave().setId(1L).setUserId(userId).setGameKey(gameKey)
                .setStateJson(json).setSchemaVer(1);
    }

    @Test
    @DisplayName("读存档：有记录返回实体")
    void getByUserAndGame_found() {
        when(mapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                .thenReturn(existing(10L, "sokoban", "{\"lv\":3}"));

        GameUserSave save = service.getByUserAndGame(10L, "sokoban");

        assertNotNull(save);
        assertEquals("{\"lv\":3}", save.getStateJson());
    }

    @Test
    @DisplayName("读存档：无记录返回 null（首次进入正常态）")
    void getByUserAndGame_notFound() {
        when(mapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(null);

        assertNull(service.getByUserAndGame(10L, "sokoban"));
    }

    @Test
    @DisplayName("写存档：已存在则更新，不重复插入")
    void saveState_updateExisting() {
        when(mapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(mapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                .thenReturn(existing(10L, "sokoban", "{\"lv\":4}"));

        GameUserSave save = service.saveState(10L, "sokoban", "{\"lv\":4}", 1);

        assertEquals("{\"lv\":4}", save.getStateJson());
        verify(mapper, never()).insert(any(GameUserSave.class));
    }

    @Test
    @DisplayName("写存档：不存在则首写插入")
    void saveState_insertFirst() {
        when(mapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);
        when(mapper.insert(any(GameUserSave.class))).thenReturn(1);

        GameUserSave save = service.saveState(10L, "sokoban", "{\"lv\":1}", 1);

        assertNotNull(save);
        assertEquals("{\"lv\":1}", save.getStateJson());
        verify(mapper).insert(any(GameUserSave.class));
    }

    @Test
    @DisplayName("写存档：并发首写撞唯一索引时回退为更新")
    void saveState_duplicateKeyFallback() {
        when(mapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);
        when(mapper.insert(any(GameUserSave.class))).thenThrow(new DuplicateKeyException("uk_user_game"));
        when(mapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                .thenReturn(existing(10L, "sokoban", "{\"lv\":2}"));

        GameUserSave save = service.saveState(10L, "sokoban", "{\"lv\":2}", 1);

        assertNotNull(save);
        verify(mapper, org.mockito.Mockito.times(2)).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("写存档：非法 JSON 拒绝")
    void saveState_invalidJson() {
        assertThrows(CustomExceptions.class,
                () -> service.saveState(10L, "sokoban", "{not json", 1));
        verify(mapper, never()).insert(any(GameUserSave.class));
    }

    @Test
    @DisplayName("写存档：空内容拒绝")
    void saveState_blank() {
        assertThrows(CustomExceptions.class, () -> service.saveState(10L, "sokoban", "  ", 1));
    }

    @Test
    @DisplayName("写存档：超过大小上限拒绝")
    void saveState_tooLarge() {
        String big = "x".repeat(64 * 1024 + 1);
        assertThrows(CustomExceptions.class, () -> service.saveState(10L, "sokoban", big, 1));
    }

    @Test
    @DisplayName("写存档：gameKey 为空拒绝")
    void saveState_blankGameKey() {
        assertThrows(CustomExceptions.class, () -> service.saveState(10L, "", "{}", 1));
    }
}
