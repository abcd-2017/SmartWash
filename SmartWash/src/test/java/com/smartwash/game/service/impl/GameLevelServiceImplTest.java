package com.smartwash.game.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.game.entity.GameConfig;
import com.smartwash.game.entity.GameLevel;
import com.smartwash.game.mapper.GameConfigMapper;
import com.smartwash.game.mapper.GameLevelMapper;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 游戏关卡服务测试。
 * 覆盖：列表（全部/仅启用）、upsert（新建/更新）、删除、版本号递增、非法 JSON 拒绝。
 * 纯 Mockito 单测，baseMapper / gameConfigMapper 手动注入。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameLevelServiceImplTest {

    @Mock
    private GameLevelMapper mapper;
    @Mock
    private GameConfigMapper gameConfigMapper;

    private GameLevelServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // 纯单测环境无 MyBatis 容器：手动初始化实体 TableInfo，供 lambda 列名解析
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, GameLevel.class);
        TableInfoHelper.initTableInfo(assistant, GameConfig.class);
    }

    @BeforeEach
    void setUp() {
        service = new GameLevelServiceImpl(gameConfigMapper);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    private GameConfig config() {
        return new GameConfig().setId(1L).setGameKey("sokoban").setLevelsVersion(1);
    }

    @Test
    @DisplayName("关卡列表：返回该游戏全部关卡")
    void listByGame() {
        when(mapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(new GameLevel().setGameKey("sokoban").setLevelNo(1)));

        List<GameLevel> list = service.listByGame("sokoban");

        assertEquals(1, list.size());
    }

    @Test
    @DisplayName("upsert：新建关卡并递增版本号")
    void upsert_create() {
        when(gameConfigMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(config());
        when(mapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(null);
        when(mapper.insert(any(GameLevel.class))).thenReturn(1);

        GameLevel level = service.upsertLevel("sokoban", 1, "第一关", "[\"######\"]", 1, 1, 10, null);

        assertNotNull(level);
        assertEquals(1, level.getLevelNo());
        verify(mapper).insert(any(GameLevel.class));
        verify(gameConfigMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("upsert：已存在则更新，不重复插入")
    void upsert_update() {
        when(gameConfigMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(config());
        when(mapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                .thenReturn(new GameLevel().setId(9L).setGameKey("sokoban").setLevelNo(1));
        when(mapper.updateById(any(GameLevel.class))).thenReturn(1);

        service.upsertLevel("sokoban", 1, "改名字", "[\"######\"]", 1, 1, 10, null);

        verify(mapper).updateById(any(GameLevel.class));
        verify(mapper, never()).insert(any(GameLevel.class));
    }

    @Test
    @DisplayName("upsert：非法 JSON 拒绝")
    void upsert_invalidJson() {
        assertThrows(CustomExceptions.class,
                () -> service.upsertLevel("sokoban", 1, "关", "{bad", 1, 1, 10, null));
        verify(mapper, never()).insert(any(GameLevel.class));
    }

    @Test
    @DisplayName("upsert：关卡序号非法拒绝")
    void upsert_invalidLevelNo() {
        assertThrows(CustomExceptions.class,
                () -> service.upsertLevel("sokoban", 0, "关", "[]", 1, 1, 10, null));
    }

    @Test
    @DisplayName("删除关卡：按序号删除并递增版本号")
    void deleteLevel() {
        when(gameConfigMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(config());
        when(mapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

        service.deleteLevel("sokoban", 3);

        verify(mapper).delete(any(LambdaQueryWrapper.class));
        verify(gameConfigMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("读取版本号：缺省返回 1")
    void getLevelVersion_default() {
        when(gameConfigMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                .thenReturn(new GameConfig().setGameKey("sokoban"));

        assertEquals(1, service.getLevelVersion("sokoban"));
    }

    private static <T> T isNull() {
        return org.mockito.ArgumentMatchers.isNull();
    }
}
