package com.smartwash.points.service.impl;

import com.smartwash.exception.CustomExceptions;
import com.smartwash.points.config.PointsProperties;
import com.smartwash.points.constant.PointReason;
import com.smartwash.points.entity.PointAccount;
import com.smartwash.points.entity.PointLedger;
import com.smartwash.points.entity.PointSignIn;
import com.smartwash.points.mapper.PointAccountMapper;
import com.smartwash.points.mapper.PointLedgerMapper;
import com.smartwash.points.mapper.PointSignInMapper;
import com.smartwash.points.service.IPointsService;
import com.smartwash.points.vo.LeaderboardUserVo;
import com.smartwash.points.vo.LeaderboardVo;
import com.smartwash.points.vo.PointLedgerItemVo;
import com.smartwash.points.vo.PointsOverviewVo;
import com.smartwash.points.vo.SignInResultVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 平台积分服务实现。
 *
 * <p>并发三防（签到幂等）：
 * ① DB 闸门 —— point_sign_in 的 uk_sign_user_date(user_id, sign_date) 唯一索引兜底；
 * ② 事务内 INSERT point_sign_in 捕获 DuplicateKeyException → 判幂等正常返回（不重复加分）；
 * ③ 账户加分走条件 UPDATE 单语句自增（balance = balance + delta，InnoDB 行锁串行化），不存在读改写竞态。</p>
 *
 * <p>排行榜一致性口径：point_ledger 为事实源，Redis ZSet 仅是月榜展示缓存；
 * ZINCRBY 在加分事务提交后（afterCommit）异步补偿式同步，同步失败仅 warn 不回滚主流程，
 * 榜与库允许短暂不一致（极端情况下少计某次签到分），不提供自动重建（后置：可由 ledger 重放修复）。</p>
 *
 * <p>防刷取舍：@RateLimit 在 feat/backend-rate-limit 分支尚未合入 main，本模块不依赖它；
 * 签到本身有 DB 唯一索引幂等（重复请求无副作用），故只需轻量 Redis 每日尝试计数
 * （风格对齐 DivUsageService / 验证码频控）防止恶意高频打库；/me 与 leaderboard 为只读索引查询，不设限。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsServiceImpl implements IPointsService {

    private final PointAccountMapper accountMapper;
    private final PointLedgerMapper ledgerMapper;
    private final PointSignInMapper signInMapper;
    private final StringRedisTemplate redisTemplate;
    private final PointsProperties props;

    /** 积分月榜 ZSet key 前缀（db 3，沿用现有实例）：points:lb:{yyyyMM}，成员 = userId */
    private static final String LEADERBOARD_KEY_PREFIX = "points:lb:";
    /** 月榜 key 按自然月滚动，TTL 45 天覆盖整月 + 跨月冗余窗口，避免冷榜永久滞留 */
    private static final Duration LEADERBOARD_TTL = Duration.ofDays(45);
    /** 月榜成员/查询使用的月份格式 */
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    /** 签到尝试频控 Redis key：points:rl:signin:{userId}:{yyyyMMdd}（TTL 25h 对齐 DivUsageService） */
    private static final String SIGNIN_ATTEMPT_KEY = "points:rl:signin:%d:%s";
    private static final Duration SIGNIN_ATTEMPT_TTL = Duration.ofHours(25);
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ==================== 签到 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SignInResultVo signIn(Long userId) {
        // 0. 轻量防刷：Redis 每日尝试计数（方法入参 userId 来自登录上下文，非前端可篡改字段）
        requireSignInAttempt(userId);

        LocalDate today = LocalDate.now();

        // 1. 幂等快路径：当日已有签到记录 → 直接幂等返回（省一次写冲突，也覆盖大部分重复点击）
        PointSignIn existed = signInMapper.selectByUserIdAndDate(userId, today);
        if (existed != null) {
            return buildIdempotentResult(userId, existed);
        }

        // 2. 连签判定：昨日有签到记录则 streak+1，否则重置 1（断签重置）
        PointSignIn yesterday = signInMapper.selectByUserIdAndDate(userId, today.minusDays(1));
        int streak = (yesterday != null) ? yesterday.getStreak() + 1 : 1;

        // 3. 防线①②：INSERT 签到记录，唯一索引兜底并发同日签到；
        //    冲突 = 并发赢家的INSERT 已生效，本方转为幂等返回（注意：此刻尚未加分，无需回滚补偿）
        try {
            signInMapper.insert(new PointSignIn().setUserId(userId).setSignDate(today).setStreak(streak));
        } catch (DuplicateKeyException e) {
            log.info("并发签到幂等命中（唯一索引兜底）, userId: {}", userId);
            PointSignIn winner = signInMapper.selectByUserIdAndDate(userId, today);
            if (winner == null) {
                // 理论不可达（INSERT 冲突后必可读到赢家记录）；防御性兜底，避免 NPE 外漏
                throw new IllegalStateException("签到冲突后读取签到记录失败, userId: " + userId);
            }
            return buildIdempotentResult(userId, winner);
        }

        // 4. 积分计算（规则集中在 PointsProperties，禁止魔法数）：
        //    基础分恒发；连签奖励在 streak 恰为周期整数倍时发放（默认第 7/14/21…天）
        int step = props.getSignIn().getStreakBonusStep();
        int basePoints = props.getSignIn().getBasePoints();
        int bonusPoints = (streak % step == 0) ? props.getSignIn().getStreakBonusPoints() : 0;
        int totalAward = basePoints + bonusPoints;

        // 5. 防线③：懒创建账户 + 条件 UPDATE 单语句自增加分
        ensureAccount(userId);
        if (accountMapper.addPoints(userId, totalAward, totalAward) == 0) {
            // 账户已在上面懒创建，影响 0 行说明数据异常，抛出使整个事务回滚（含签到记录）
            throw new IllegalStateException("积分账户加分影响 0 行（账户缺失或异常）, userId: " + userId);
        }
        int balanceAfter = selectBalanceAfter(userId);

        // 6. append-only 流水（balance_after 连续性：同一事务内读自身更新快照；
        //    同用户并发写入已被签到唯一索引挡住，v1 加分来源唯一，快照即连续事实）
        insertLedger(userId, basePoints, PointReason.SIGN_IN, today, balanceAfter - bonusPoints);
        if (bonusPoints > 0) {
            insertLedger(userId, bonusPoints, PointReason.STREAK_BONUS, today, balanceAfter);
        }

        // 7. 事务提交后同步月榜 ZSet（失败仅 warn，不影响主流程）
        registerLeaderboardSyncAfterCommit(userId, totalAward, YearMonth.from(today));

        log.info("用户签到成功, userId: {}, streak: {}, award: {}", userId, streak, totalAward);
        return buildSignedResult(streak, totalAward, balanceAfter);
    }

    /**
     * 懒创建积分账户：并发首建由 uk_point_account_user 唯一索引兜底，冲突吞掉继续走加分。
     */
    private void ensureAccount(Long userId) {
        if (accountMapper.selectByUserId(userId) != null) {
            return;
        }
        try {
            accountMapper.insert(new PointAccount().setUserId(userId).setBalance(0).setTotalEarned(0));
        } catch (DuplicateKeyException e) {
            log.info("积分账户并发首建幂等命中, userId: {}", userId);
        }
    }

    /**
     * 读取加分后的余额快照：同事务内可见自身更新；同用户并发加分已被签到唯一索引串行化。
     */
    private int selectBalanceAfter(Long userId) {
        PointAccount account = accountMapper.selectByUserId(userId);
        if (account == null) {
            throw new IllegalStateException("加分后读取账户失败, userId: " + userId);
        }
        return account.getBalance();
    }

    private void insertLedger(Long userId, int delta, PointReason reason, LocalDate refDate, int balanceAfter) {
        PointLedger ledger = new PointLedger()
                .setUserId(userId)
                .setDelta(delta)
                .setReason(reason.code())
                .setRefDate(refDate)
                .setBalanceAfter(balanceAfter);
        ledgerMapper.insert(ledger);
    }

    private SignInResultVo buildIdempotentResult(Long userId, PointSignIn existed) {
        PointAccount account = accountMapper.selectByUserId(userId);
        SignInResultVo vo = new SignInResultVo();
        vo.setSigned(false);
        vo.setAlreadySigned(true);
        vo.setStreak(existed.getStreak());
        vo.setPointsAwarded(0);
        vo.setBalanceAfter(account != null ? account.getBalance() : 0);
        return vo;
    }

    private SignInResultVo buildSignedResult(int streak, int awarded, int balanceAfter) {
        SignInResultVo vo = new SignInResultVo();
        vo.setSigned(true);
        vo.setAlreadySigned(false);
        vo.setStreak(streak);
        vo.setPointsAwarded(awarded);
        vo.setBalanceAfter(balanceAfter);
        return vo;
    }

    // ==================== 我的积分概览 ====================

    @Override
    public PointsOverviewVo getOverview(Long userId) {
        LocalDate today = LocalDate.now();

        PointAccount account = accountMapper.selectByUserId(userId);
        PointSignIn todayRecord = signInMapper.selectByUserIdAndDate(userId, today);
        boolean signedToday = todayRecord != null;

        // 连签口径：已签用签到时点 streak；未签用预览（昨日 streak+1），便于端上提示"今天签将达 N 天"
        int streak;
        if (signedToday) {
            streak = todayRecord.getStreak();
        } else {
            PointSignIn yesterday = signInMapper.selectByUserIdAndDate(userId, today.minusDays(1));
            streak = (yesterday != null) ? yesterday.getStreak() + 1 : 0;
        }

        PointsOverviewVo vo = new PointsOverviewVo();
        vo.setBalance(account != null ? account.getBalance() : 0);
        vo.setTotalEarned(account != null ? account.getTotalEarned() : 0);
        vo.setSignedToday(signedToday);
        vo.setStreak(streak);
        vo.setNextBonusIn(nextBonusIn(signedToday, streak));
        vo.setRecentLedger(buildRecentLedger(userId, props.getLeaderboard().getRecentLedgerSize()));
        return vo;
    }

    /**
     * 距下次连签奖励还需的签到次数（含今日，若今日未签）：
     * 已签且当前 streak 恰为周期整数倍 → 本周期奖励已发，下轮还需整 step 天；
     * 未签且"今日签恰好到达奖励日"（预览 streak % step == 0 且 > 0）→ 还需 1 次。
     */
    private int nextBonusIn(boolean signedToday, int streak) {
        int step = props.getSignIn().getStreakBonusStep();
        int mod = streak % step;
        if (signedToday) {
            return (mod == 0) ? step : step - mod;
        }
        return (streak > 0 && mod == 0) ? 1 : step - mod;
    }

    private List<PointLedgerItemVo> buildRecentLedger(Long userId, int limit) {
        List<PointLedger> ledgers = ledgerMapper.selectRecentByUserId(userId, limit);
        List<PointLedgerItemVo> items = new ArrayList<>(ledgers.size());
        for (PointLedger ledger : ledgers) {
            PointLedgerItemVo item = new PointLedgerItemVo();
            item.setDelta(ledger.getDelta());
            item.setReason(ledger.getReason());
            item.setReasonDesc(resolveReasonDesc(ledger.getReason()));
            item.setRefDate(ledger.getRefDate());
            item.setBalanceAfter(ledger.getBalanceAfter());
            item.setCreatedAt(ledger.getCreatedAt());
            items.add(item);
        }
        return items;
    }

    /**
     * reason 枚举集中翻译：未知值（历史数据/后置枚举）原样透传，不抛异常。
     */
    private String resolveReasonDesc(String reason) {
        try {
            return PointReason.valueOf(reason).getDesc();
        } catch (IllegalArgumentException e) {
            return reason;
        }
    }

    // ==================== 月榜 ====================

    @Override
    public LeaderboardVo getMonthLeaderboard(String scope, Long userId) {
        if (!"month".equals(scope)) {
            // 口径集中校验：v1 仅支持 month，避免前端传错静默兜底成另一个榜
            throw new CustomExceptions("暂仅支持 month 口径");
        }
        String month = YearMonth.now().format(MONTH_FMT);
        String key = LEADERBOARD_KEY_PREFIX + month;

        LeaderboardVo vo = new LeaderboardVo();
        vo.setScope("month");
        vo.setMonth(month);
        vo.setEntries(Collections.emptyList());

        // ZREVRANGE 0..size-1 WITHSCORES：ZSet 读失败降级为空榜（展示型数据，不阻断页面）
        Set<ZSetOperations.TypedTuple<String>> tuples;
        try {
            long size = props.getLeaderboard().getSize();
            tuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, size - 1);
        } catch (Exception e) {
            log.warn("积分月榜读取失败（降级为空榜）, key: {}", key, e);
            return vo;
        }
        if (tuples == null || tuples.isEmpty()) {
            vo.setMy(buildMyRank(userId, key, null));
            return vo;
        }

        // 成员为 userId（String），批量补手机号/头像做脱敏展示
        List<Long> rankedUserIds = new ArrayList<>(tuples.size());
        Map<Long, Double> scoreByUser = new HashMap<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Long uid = parseUserId(tuple.getValue());
            if (uid != null) {
                rankedUserIds.add(uid);
                scoreByUser.put(uid, tuple.getScore());
            }
        }

        List<LeaderboardUserVo> users = rankedUserIds.isEmpty()
                ? Collections.emptyList()
                : accountMapper.selectLeaderboardUsers(rankedUserIds);
        Map<Long, LeaderboardUserVo> userById = new HashMap<>(users.size());
        for (LeaderboardUserVo user : users) {
            userById.put(user.getUserId(), user);
        }

        List<LeaderboardVo.EntryVo> entries = new ArrayList<>(rankedUserIds.size());
        int rank = 1;
        for (Long uid : rankedUserIds) {
            LeaderboardUserVo user = userById.get(uid);
            // 用户已被物理删除（本项目无逻辑删除）则跳过该名次条目，榜单实际条数可少于 size
            if (user == null) {
                continue;
            }
            LeaderboardVo.EntryVo entry = new LeaderboardVo.EntryVo();
            entry.setRank(rank++);
            entry.setUserId(uid);
            entry.setMaskedPhone(maskPhone(user.getPhoneNumber()));
            entry.setAvatar(user.getAvatar());
            entry.setPoints(LeaderboardVo.toIntPoints(BigDecimal.valueOf(scoreByUser.get(uid))));
            entries.add(entry);
        }
        vo.setEntries(entries);
        vo.setMy(buildMyRank(userId, key, scoreByUser.get(userId)));
        return vo;
    }

    private LeaderboardVo.MyRankVo buildMyRank(Long userId, String key, Double score) {
        LeaderboardVo.MyRankVo my = new LeaderboardVo.MyRankVo();
        try {
            Long zRevRank = redisTemplate.opsForZSet().reverseRank(key, String.valueOf(userId));
            my.setRank(zRevRank == null ? null : zRevRank.intValue() + 1);
        } catch (Exception e) {
            log.warn("积分月榜我的排名读取失败, key: {}, userId: {}", key, userId, e);
            my.setRank(null);
        }
        my.setPoints(LeaderboardVo.toIntPoints(score == null ? null : BigDecimal.valueOf(score)));
        return my;
    }

    private Long parseUserId(String member) {
        try {
            return Long.valueOf(member);
        } catch (NumberFormatException e) {
            // 成员格式异常不炸榜单，跳过该条
            return null;
        }
    }

    /**
     * 事务提交后同步月榜 ZSet（ZINCRBY + TTL）。
     * 月份在事务内固化（签到月口径，与流水 ref_date 对齐），避免跨月零点提交漂移；
     * 同步失败仅 warn —— 榜与库允许短暂不一致（一致性口径见类注释）。
     */
    private void registerLeaderboardSyncAfterCommit(Long userId, int delta, YearMonth month) {
        String key = LEADERBOARD_KEY_PREFIX + month.format(MONTH_FMT);
        Runnable syncTask = () -> syncLeaderboard(key, String.valueOf(userId), delta);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    syncTask.run();
                }
            });
        } else {
            // 无事务上下文（如单测直调）：保持同步语义便于测试覆盖
            syncTask.run();
        }
    }

    private void syncLeaderboard(String key, String member, int delta) {
        try {
            redisTemplate.opsForZSet().incrementScore(key, member, delta);
            redisTemplate.expire(key, LEADERBOARD_TTL);
        } catch (Exception e) {
            log.warn("积分月榜 ZSet 同步失败（不影响主流程,榜与库允许短暂不一致）, key: {}, member: {}", key, member, e);
        }
    }

    // ==================== 防刷 ====================

    /**
     * 轻量防刷：Redis 每日尝试计数（对齐 DivUsageService increment+TTL 风格）。
     * 签到接口本身幂等（唯一索引），重复请求无副作用，此处仅防恶意高频穿透打库。
     * Redis 不可用时放行（防刷可降级，幂等正确性由 DB 保证）。
     */
    private void requireSignInAttempt(Long userId) {
        try {
            String key = String.format(SIGNIN_ATTEMPT_KEY, userId, LocalDate.now().format(DAY_FMT));
            Long attempts = redisTemplate.opsForValue().increment(key);
            if (attempts != null && attempts == 1) {
                redisTemplate.expire(key, SIGNIN_ATTEMPT_TTL);
            }
            if (attempts != null && attempts > props.getSignIn().getAttemptDailyLimit()) {
                log.warn("签到尝试超频控上限, userId: {}, attempts: {}", userId, attempts);
                throw new CustomExceptions("操作过于频繁，请明天再试");
            }
        } catch (CustomExceptions e) {
            throw e;
        } catch (Exception e) {
            log.warn("签到防刷计数异常（降级放行,幂等由 DB 兜底）, userId: {}", userId, e);
        }
    }

    /**
     * 手机号脱敏，对齐 LoginController.maskPhone 同一正则口径
     */
    private String maskPhone(String phone) {
        if (phone == null) {
            return "";
        }
        return phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
    }
}
