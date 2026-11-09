package com.smartwash.game.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.game.entity.GameConfig;
import com.smartwash.game.entity.GameScoreEntry;
import com.smartwash.game.mapper.GameConfigMapper;
import com.smartwash.game.mapper.GameScoreEntryMapper;
import com.smartwash.game.vo.GameLeaderboardVo;
import com.smartwash.game.vo.GameScoreUserVo;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 游戏成绩 / 排行榜服务测试。
 * 覆盖：首次上报、刷新最佳（desc）、未刷新最佳只累计次数、asc 越小越好、未开启榜拒绝、
 * 排行榜缓存命中 / 回源 DB / 未开启返回空。
 * 纯 Mockito 单测，baseMapper / redisTemplate / gameConfigMapper 手动注入。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameScoreServiceImplTest {

    @Mock
    private GameScoreEntryMapper mapper;
    @Mock
    private GameConfigMapper gameConfigMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ZSetOperations<String, String> zSetOps;

    private GameScoreServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // 纯单测环境无 MyBatis 容器：手动初始化实体 TableInfo，供 lambda 列名解析
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, GameScoreEntry.class);
        TableInfoHelper.initTableInfo(assistant, GameConfig.class);
    }

    @BeforeEach
    void setUp() {
        service = new GameScoreServiceImpl(redisTemplate, gameConfigMapper);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    private GameConfig config(String gameKey, int metricEnabled, String order) {
        return new GameConfig().setId(1L).setGameKey(gameKey)
                .setMetricEnabled(metricEnabled).setMetricOrder(order).setMetricLabel("最高分");
    }

    private GameScoreEntry entry(Long userId, String gameKey, long value) {
        return new GameScoreEntry().setId(1L).setUserId(userId).setGameKey(gameKey)
                .setMetricValue(value).setPlayedCount(1);
    }

    // ==================== 上报 ====================

    @Test
    @DisplayName("首次上报：插入新条目")
    void reportScore_firstInsert() {
        when(gameConfigMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                .thenReturn(config("sokoban", 1, "desc"));
        when(mapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(null);
        when(mapper.insert(any(GameScoreEntry.class))).thenReturn(1);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        GameScoreEntry saved = service.reportScore(10L, "sokoban", 100L, null, null);

        assertEquals(100L, saved.getMetricValue());
        verify(mapper).insert(any(GameScoreEntry.class));
    }

    @Test
    @DisplayName("desc 游戏：刷新最佳才更新排序值")
    void reportScore_descRefresh() {
        when(gameConfigMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                .thenReturn(config("sokoban", 1, "desc"));
        when(mapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                .thenReturn(entry(10L, "sokoban", 100L));
        when(mapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        service.reportScore(10L, "sokoban", 150L, null, null);

        verify(mapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("desc 游戏：未刷新最佳只累计次数，不动排序值")
    void reportScore_descNoRefresh() {
        when(gameConfigMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                .thenReturn(config("sokoban", 1, "desc"));
        when(mapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                .thenReturn(entry(10L, "sokoban", 100L));
        when(mapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        service.reportScore(10L, "sokoban", 80L, null, null);

        // 仅一次 update（累计次数），不触发缓存同步
        verify(mapper).update(isNull(), any(LambdaUpdateWrapper.class));
        verify(redisTemplate, never()).opsForZSet();
    }

    @Test
    @DisplayName("asc 游戏：步数更少才刷新最佳")
    void reportScore_ascRefresh() {
        when(gameConfigMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                .thenReturn(config("minesweeper", 1, "asc"));
        when(mapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                .thenReturn(entry(10L, "minesweeper", 120L));
        when(mapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        service.reportScore(10L, "minesweeper", 90L, null, null);

        verify(mapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("未开启排行榜：拒绝上报")
    void reportScore_disabled() {
        when(gameConfigMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                .thenReturn(config("falling-sand", 0, "desc"));

        assertThrows(CustomExceptions.class,
                () -> service.reportScore(10L, "falling-sand", 100L, null, null));
        verify(mapper, never()).insert(any(GameScoreEntry.class));
    }

    // ==================== 排行榜 ====================

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("排行榜：缓存命中直接返回")
    void leaderboard_cacheHit() {
        when(gameConfigMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                .thenReturn(config("sokoban", 1, "desc"));
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
        tuples.add(new DefaultTypedTuple<>("10", 150.0));
        when(zSetOps.reverseRangeWithScores(anyString(), anyLong(), anyLong())).thenReturn(tuples);
        GameScoreUserVo user = new GameScoreUserVo();
        user.setUserId(10L);
        user.setMaskedPhone("138****1234");
        when(mapper.selectScoreUsers(any())).thenReturn(List.of(user));
        when(mapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(null);

        GameLeaderboardVo vo = service.getLeaderboard("sokoban", 20, 10L);

        assertEquals(1, vo.getEntries().size());
        assertEquals(150L, vo.getEntries().get(0).getMetricValue());
        assertEquals("138****1234", vo.getEntries().get(0).getMaskedPhone());
    }

    @Test
    @DisplayName("排行榜：缓存未命中回源 DB")
    void leaderboard_cacheMissFallback() {
        when(gameConfigMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                .thenReturn(config("sokoban", 1, "desc"));
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.reverseRangeWithScores(anyString(), anyLong(), anyLong())).thenReturn(Set.of());
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(mapper.selectTopN(eq("sokoban"), anyInt(), anyBoolean()))
                .thenReturn(List.of(entry(10L, "sokoban", 100L)));
        GameScoreUserVo user = new GameScoreUserVo();
        user.setUserId(10L);
        user.setMaskedPhone("138****1234");
        when(mapper.selectScoreUsers(any())).thenReturn(List.of(user));

        GameLeaderboardVo vo = service.getLeaderboard("sokoban", 20, null);

        assertEquals(1, vo.getEntries().size());
        verify(mapper).selectTopN(eq("sokoban"), anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("排行榜：未开启返回空榜")
    void leaderboard_disabled() {
        when(gameConfigMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                .thenReturn(config("falling-sand", 0, "desc"));

        GameLeaderboardVo vo = service.getLeaderboard("falling-sand", 20, 10L);

        assertTrue(vo.getEntries().isEmpty());
        assertNull(vo.getMy());
    }
}
