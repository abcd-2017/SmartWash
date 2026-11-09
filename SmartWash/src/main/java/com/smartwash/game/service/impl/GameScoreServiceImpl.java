package com.smartwash.game.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.game.entity.GameConfig;
import com.smartwash.game.entity.GameScoreEntry;
import com.smartwash.game.from.GameScoreSearchFrom;
import com.smartwash.game.mapper.GameConfigMapper;
import com.smartwash.game.mapper.GameScoreEntryMapper;
import com.smartwash.game.service.GameScoreService;
import com.smartwash.game.vo.GameLeaderboardVo;
import com.smartwash.game.vo.GameScoreAdminVo;
import com.smartwash.game.vo.GameScoreUserVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 游戏成绩 / 排行榜服务实现。
 *
 * <p>一致性口径：game_score_entry 为事实源，Redis ZSet（game:lb:{gameKey}）仅展示缓存；
 * 缓存未命中回源 DB 并回填，同步失败仅 warn、允许短暂不一致（与 points:lb 同一纪律）。</p>
 *
 * <p>排序方向取自 game_config.metric_order：desc 用 ZREVRANGE（分数类），asc 用 ZRANGE（步数/用时类）。
 * 未开启排行榜（metric_enabled=0）的游戏直接返回空榜，不落库、不同步。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameScoreServiceImpl extends ServiceImpl<GameScoreEntryMapper, GameScoreEntry>
        implements GameScoreService {

    /** 排行榜 ZSet key：game:lb:{gameKey}，成员 = userId */
    private static final String LEADERBOARD_KEY_PREFIX = "game:lb:";
    /** 排行榜缓存 TTL：30 天（过期后回源 DB 回填，展示型数据可接受） */
    private static final long LEADERBOARD_TTL_DAYS = 30;

    private static final String ORDER_DESC = "desc";
    private static final String ORDER_ASC = "asc";

    private final StringRedisTemplate redisTemplate;
    private final GameConfigMapper gameConfigMapper;

    @Override
    public GameScoreEntry reportScore(Long userId, String gameKey, Long metricValue,
                                     Long metricExtra, String metricJson) {
        requireGameKey(gameKey);
        if (metricValue == null) {
            throw new CustomExceptions("成绩值不能为空");
        }
        GameConfig config = requireLeaderboardEnabled(gameKey);

        GameScoreEntry existing = getOne(new LambdaQueryWrapper<GameScoreEntry>()
                .eq(GameScoreEntry::getUserId, userId)
                .eq(GameScoreEntry::getGameKey, gameKey), false);

        boolean first = existing == null;
        boolean better = first || isBetter(gameKey, metricValue, existing.getMetricValue(), config.getMetricOrder());
        if (!better) {
            // 未刷新最佳：仅累计次数与最近时间，不动排序值
            baseMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<GameScoreEntry>()
                    .eq(GameScoreEntry::getUserId, userId)
                    .eq(GameScoreEntry::getGameKey, gameKey)
                    .setSql("played_count = played_count + 1")
                    .set(GameScoreEntry::getLastPlayedAt, LocalDateTime.now()));
            return getOne(new LambdaQueryWrapper<GameScoreEntry>()
                    .eq(GameScoreEntry::getUserId, userId)
                    .eq(GameScoreEntry::getGameKey, gameKey), false);
        }

        GameScoreEntry saved;
        if (first) {
            saved = new GameScoreEntry()
                    .setUserId(userId)
                    .setGameKey(gameKey)
                    .setMetricValue(metricValue)
                    .setMetricExtra(metricExtra)
                    .setMetricJson(metricJson)
                    .setPlayedCount(1)
                    .setLastPlayedAt(LocalDateTime.now());
            save(saved);
        } else {
            baseMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<GameScoreEntry>()
                    .eq(GameScoreEntry::getUserId, userId)
                    .eq(GameScoreEntry::getGameKey, gameKey)
                    .set(GameScoreEntry::getMetricValue, metricValue)
                    .set(GameScoreEntry::getMetricExtra, metricExtra)
                    .set(GameScoreEntry::getMetricJson, metricJson)
                    .setSql("played_count = played_count + 1")
                    .set(GameScoreEntry::getLastPlayedAt, LocalDateTime.now()));
            saved = getOne(new LambdaQueryWrapper<GameScoreEntry>()
                    .eq(GameScoreEntry::getUserId, userId)
                    .eq(GameScoreEntry::getGameKey, gameKey), false);
        }

        syncToCache(gameKey, userId, metricValue);
        return saved;
    }

    @Override
    public Page<GameScoreAdminVo> adminPage(GameScoreSearchFrom from) {
        Page<GameScoreAdminVo> page = new Page<>(from.getPage(), from.getSize());
        return baseMapper.selectAdminPage(page, from.getGameKey());
    }

    @Override
    public GameLeaderboardVo getLeaderboard(String gameKey, int limit, Long userId) {
        requireGameKey(gameKey);
        GameLeaderboardVo vo = new GameLeaderboardVo();
        vo.setGameKey(gameKey);
        vo.setEntries(Collections.emptyList());

        GameConfig config = gameConfigMapper.selectOne(new LambdaQueryWrapper<GameConfig>()
                .eq(GameConfig::getGameKey, gameKey), false);
        if (config == null || config.getMetricEnabled() == null || config.getMetricEnabled() != 1) {
            return vo;
        }
        String order = normalizeOrder(config.getMetricOrder());
        vo.setMetricOrder(order);
        vo.setMetricLabel(config.getMetricLabel());

        int size = limit <= 0 ? 20 : Math.min(limit, 100);

        // 1) 先读缓存；未命中回源 DB 并回填
        List<GameScoreEntry> top = readFromCache(gameKey, size, order);
        if (top == null) {
            top = baseMapper.selectTopN(gameKey, size, ORDER_ASC.equals(order));
            backfillCache(gameKey, top);
        }
        if (top.isEmpty()) {
            vo.setMy(buildMyRank(gameKey, userId, order, null));
            return vo;
        }

        // 2) 批量补用户信息（脱敏）
        List<Long> userIds = new ArrayList<>(top.size());
        for (GameScoreEntry entry : top) {
            userIds.add(entry.getUserId());
        }
        List<GameScoreUserVo> users = baseMapper.selectScoreUsers(userIds);
        Map<Long, GameScoreUserVo> userById = new HashMap<>(users.size());
        for (GameScoreUserVo user : users) {
            userById.put(user.getUserId(), user);
        }

        // 3) 拼装（用户已物理删除则跳过，榜单实际条数可少于 size）
        List<GameLeaderboardVo.EntryVo> entries = new ArrayList<>(top.size());
        int rank = 1;
        for (GameScoreEntry entry : top) {
            GameScoreUserVo user = userById.get(entry.getUserId());
            if (user == null) {
                continue;
            }
            GameLeaderboardVo.EntryVo item = new GameLeaderboardVo.EntryVo();
            item.setRank(rank++);
            item.setUserId(entry.getUserId());
            item.setMaskedPhone(user.getMaskedPhone());
            item.setAvatar(user.getAvatar());
            item.setMetricValue(entry.getMetricValue());
            item.setMetricExtra(entry.getMetricExtra());
            entries.add(item);
        }
        vo.setEntries(entries);

        // 4) 我的排名
        GameScoreEntry mine = null;
        if (userId != null) {
            mine = getOne(new LambdaQueryWrapper<GameScoreEntry>()
                    .eq(GameScoreEntry::getUserId, userId)
                    .eq(GameScoreEntry::getGameKey, gameKey), false);
        }
        vo.setMy(buildMyRank(gameKey, userId, order, mine == null ? null : mine.getMetricValue()));
        return vo;
    }

    // ==================== 缓存 ====================

    /** 缓存读：命中返回榜单，未命中/异常返回 null（由调用方回源 DB） */
    private List<GameScoreEntry> readFromCache(String gameKey, int size, String order) {
        String key = leaderboardKey(gameKey);
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples;
            if (ORDER_ASC.equals(order)) {
                tuples = redisTemplate.opsForZSet().rangeWithScores(key, 0, size - 1);
            } else {
                tuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, size - 1);
            }
            if (tuples == null || tuples.isEmpty()) {
                return null;
            }
            List<GameScoreEntry> list = new ArrayList<>(tuples.size());
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                Long uid = parseUserId(tuple.getValue());
                if (uid == null) {
                    continue;
                }
                GameScoreEntry entry = new GameScoreEntry();
                entry.setUserId(uid);
                entry.setGameKey(gameKey);
                entry.setMetricValue(tuple.getScore() == null ? 0L : tuple.getScore().longValue());
                list.add(entry);
            }
            return list;
        } catch (Exception e) {
            log.warn("游戏排行榜缓存读取失败（回源 DB）, gameKey: {}, key: {}", gameKey, key, e);
            return null;
        }
    }

    /** 缓存回填：仅在 key 不存在时写入，避免用不完整数据覆盖 */
    private void backfillCache(String gameKey, List<GameScoreEntry> top) {
        if (top.isEmpty()) {
            return;
        }
        String key = leaderboardKey(gameKey);
        try {
            Boolean exists = redisTemplate.hasKey(key);
            if (Boolean.TRUE.equals(exists)) {
                return;
            }
            for (GameScoreEntry entry : top) {
                redisTemplate.opsForZSet().add(key, String.valueOf(entry.getUserId()), entry.getMetricValue());
            }
            redisTemplate.expire(key, java.time.Duration.ofDays(LEADERBOARD_TTL_DAYS));
        } catch (Exception e) {
            log.warn("游戏排行榜缓存回填失败, gameKey: {}", gameKey, e);
        }
    }

    /** 成绩刷新后同步缓存（ZADD 覆盖旧分；缓存不存在则不主动重建，等回源） */
    private void syncToCache(String gameKey, Long userId, Long metricValue) {
        String key = leaderboardKey(gameKey);
        try {
            Boolean exists = redisTemplate.hasKey(key);
            if (Boolean.FALSE.equals(exists)) {
                return;
            }
            redisTemplate.opsForZSet().add(key, String.valueOf(userId), metricValue);
            redisTemplate.expire(key, java.time.Duration.ofDays(LEADERBOARD_TTL_DAYS));
        } catch (Exception e) {
            log.warn("游戏排行榜缓存同步失败（不影响主流程）, gameKey: {}, userId: {}", gameKey, userId, e);
        }
    }

    private GameLeaderboardVo.MyRankVo buildMyRank(String gameKey, Long userId, String order, Long score) {
        GameLeaderboardVo.MyRankVo my = new GameLeaderboardVo.MyRankVo();
        my.setMetricValue(score);
        if (userId == null) {
            return my;
        }
        String key = leaderboardKey(gameKey);
        try {
            Long rank;
            if (ORDER_ASC.equals(order)) {
                rank = redisTemplate.opsForZSet().rank(key, String.valueOf(userId));
            } else {
                rank = redisTemplate.opsForZSet().reverseRank(key, String.valueOf(userId));
            }
            my.setRank(rank == null ? null : rank.intValue() + 1);
        } catch (Exception e) {
            log.warn("游戏排行榜我的排名读取失败, gameKey: {}, userId: {}", gameKey, userId, e);
        }
        return my;
    }

    private String leaderboardKey(String gameKey) {
        return LEADERBOARD_KEY_PREFIX + gameKey;
    }

    // ==================== 工具 ====================

    private GameConfig requireLeaderboardEnabled(String gameKey) {
        GameConfig config = gameConfigMapper.selectOne(new LambdaQueryWrapper<GameConfig>()
                .eq(GameConfig::getGameKey, gameKey), false);
        if (config == null) {
            throw new CustomExceptions("游戏不存在");
        }
        if (config.getMetricEnabled() == null || config.getMetricEnabled() != 1) {
            throw new CustomExceptions("该游戏未开启排行榜");
        }
        return config;
    }

    /** 新值是否优于旧值：desc 比大、asc 比小 */
    private boolean isBetter(String gameKey, Long newValue, Long oldValue, String order) {
        if (oldValue == null) {
            return true;
        }
        return ORDER_ASC.equals(order) ? newValue < oldValue : newValue > oldValue;
    }

    private String normalizeOrder(String order) {
        return ORDER_ASC.equalsIgnoreCase(order) ? ORDER_ASC : ORDER_DESC;
    }

    private Long parseUserId(String member) {
        if (!StringUtils.hasText(member)) {
            return null;
        }
        try {
            return Long.parseLong(member);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void requireGameKey(String gameKey) {
        if (!StringUtils.hasText(gameKey)) {
            throw new CustomExceptions("gameKey 不能为空");
        }
    }
}
