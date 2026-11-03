package com.smartwash.review.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.random.RandomGenerator;
import java.util.concurrent.ThreadLocalRandom;

/**
 * FSRS-6 间隔重复调度器（纯函数，零 Spring 依赖）。
 *
 * <p>算法逐函数移植自 py-fsrs 的 {@code fsrs/scheduler.py :: review_card} 状态机
 * （open-spaced-repetition/py-fsrs，MIT License，
 * 权重取自 commit 9446cb06605c597a063aeee49f7d188d42e34dc2，2026-08-09），
 * 21 个默认权重 DEFAULT_PARAMETERS 为逐字复制，MIT 许可允许复制，出处与许可证遵此标注。</p>
 *
 * <p>状态机行为：Learning/Relearning 内为分钟级短步长（Again 步归零、Hard 停留半步、
 * Good 前进一步、走完最后一步进 Review 按天排、Easy 直接跳 Review 按天排）；
 * Review 态天级调度（Again 降级 Relearning，其余按新稳定度排天）；
 * 同日（间隔 &lt; 1 天）复习走短稳定度公式，不推进天级稳定度演化；
 * 可提取率 R = (1 + FACTOR·elapsed/S)^DECAY；
 * 间隔 = (S'/FACTOR)·(retention^(1/DECAY) - 1)，round 到天并 clamp [1, 36500]。</p>
 *
 * <p>时间统一使用 {@link LocalDateTime}（与全库 DATETIME 一致；py-fsrs 要求 UTC-aware
 * datetime 仅为其自身约定，调度只依赖时刻差值，本地时区下语义等价）。
 * fuzz 抖动默认开启（仅作用于 Review 态天级间隔），测试路径请以
 * {@link #FsrsScheduler(double[], double, long[], long[], int, boolean, RandomGenerator)}
 * 构造关闭，保证断言确定性。</p>
 */
public class FsrsScheduler {

    /**
     * FSRS-6 默认权重（21 个），逐字复制自 py-fsrs scheduler.py 的 DEFAULT_PARAMETERS
     * （open-spaced-repetition/py-fsrs@9446cb0，MIT License）。
     * w0..w3 为 Again/Hard/Good/Easy 初始稳定度，w4/w5 初始难度，w20 衰减系数。
     */
    public static final double[] DEFAULT_PARAMETERS = {
            0.212, 1.2931, 2.3065, 8.2956,
            6.4133, 0.8334, 3.0194, 0.001,
            1.8722, 0.1666, 0.796, 1.4835,
            0.0614, 0.2629, 1.6483, 0.6014,
            1.8729, 0.5425, 0.0912, 0.0658,
            0.1542
    };

    /** 目标保持率（v1 固定常量，后续可配再建表） */
    public static final double DESIRED_RETENTION = 0.9;

    /** 学习短步长（秒）：1 分钟、10 分钟 */
    public static final long[] LEARNING_STEPS = {60L, 600L};

    /** 重学短步长（秒）：10 分钟 */
    public static final long[] RELEARNING_STEPS = {600L};

    /** 天级间隔上限（天） */
    public static final int MAXIMUM_INTERVAL = 36500;

    /** 稳定度下限（天），对齐 py-fsrs STABILITY_MIN */
    private static final double STABILITY_MIN = 0.001;

    /** 难度上下限，对齐 py-fsrs MIN/MAX_DIFFICULTY */
    private static final double MIN_DIFFICULTY = 1.0;
    private static final double MAX_DIFFICULTY = 10.0;

    /** 一天的秒数 */
    private static final long SECONDS_PER_DAY = 86400L;

    /** fuzz 抖动区间，对齐 py-fsrs FUZZ_RANGES（end=inf 表示上不封顶） */
    private static final double[][] FUZZ_RANGES = {
            {2.5, 7.0, 0.15},
            {7.0, 20.0, 0.1},
            {20.0, Double.POSITIVE_INFINITY, 0.05}
    };

    /** 衰减系数 DECAY = -w20 */
    private final double decay;

    /** FACTOR = 0.9^(1/DECAY) - 1 */
    private final double factor;

    private final double[] parameters;
    private final double desiredRetention;
    private final long[] learningSteps;
    private final long[] relearningSteps;
    private final int maximumInterval;
    private final boolean enableFuzzing;
    private final RandomGenerator random;

    /** 生产默认构造：默认权重 + fuzz 开启 */
    public FsrsScheduler() {
        this(DEFAULT_PARAMETERS.clone(), DESIRED_RETENTION,
                LEARNING_STEPS.clone(), RELEARNING_STEPS.clone(),
                MAXIMUM_INTERVAL, true, ThreadLocalRandom.current());
    }

    /**
     * 全参构造（测试/未来可配化入口）。
     *
     * @param parameters      FSRS-6 的 21 个权重
     * @param desiredRetention 目标保持率（0,1) 区间
     * @param learningSteps   学习短步长（秒），非空数组
     * @param relearningSteps 重学短步长（秒），可为空数组
     * @param maximumInterval 天级间隔上限（天）
     * @param enableFuzzing   是否对 Review 态天级间隔加随机抖动
     * @param random          随机源（fuzz 用）
     */
    public FsrsScheduler(double[] parameters, double desiredRetention,
                         long[] learningSteps, long[] relearningSteps,
                         int maximumInterval, boolean enableFuzzing, RandomGenerator random) {
        if (parameters == null || parameters.length != DEFAULT_PARAMETERS.length) {
            throw new IllegalArgumentException("FSRS-6 需要 21 个权重，收到: "
                    + (parameters == null ? "null" : parameters.length));
        }
        if (learningSteps == null || learningSteps.length == 0) {
            throw new IllegalArgumentException("学习短步长不可为空");
        }
        if (relearningSteps == null) {
            throw new IllegalArgumentException("重学短步长不可为 null");
        }
        this.parameters = parameters.clone();
        this.desiredRetention = desiredRetention;
        this.learningSteps = learningSteps.clone();
        this.relearningSteps = relearningSteps.clone();
        this.maximumInterval = maximumInterval;
        this.enableFuzzing = enableFuzzing;
        this.random = random;
        this.decay = -parameters[20];
        this.factor = Math.pow(0.9, 1.0 / decay) - 1.0;
    }

    /** 一次复习的产物：更新后的新卡 + 日志（对齐 py-fsrs 返回的二元组） */
    public record ReviewResult(FsrsCard card, FsrsReviewLog log) {
    }

    /**
     * 复习一张卡（纯函数）：不修改入参 card，返回更新后的新卡与日志。
     * 对齐 py-fsrs {@code review_card(card, rating, now) -> (card', log)}。
     *
     * @param card        被复习的卡（当前 FSRS 状态）
     * @param rating      四档评分，越界抛 {@link IllegalArgumentException}
     * @param reviewDatetime 复习时刻
     */
    public ReviewResult reviewCard(FsrsCard card, FsrsRating rating, LocalDateTime reviewDatetime) {
        if (rating == null) {
            throw new IllegalArgumentException("评分不可为空");
        }
        if (reviewDatetime == null) {
            throw new IllegalArgumentException("复习时刻不可为空");
        }
        if (card == null || card.getState() == null) {
            throw new IllegalArgumentException("卡片与状态不可为空");
        }

        // 防御性拷贝，保证纯函数不污染入参（对齐 py-fsrs copy(card)）
        FsrsCard c = card.copy();

        // 距上次复习的天数（py-fsrs .days 语义）；从未复习为 null
        Long daysSinceLastReview = null;
        if (c.getLastReview() != null) {
            daysSinceLastReview = Duration.between(c.getLastReview(), reviewDatetime).toDays();
        }

        long intervalSeconds = switch (c.getState()) {
            case LEARNING -> reviewLearning(c, rating, daysSinceLastReview, reviewDatetime);
            case REVIEW -> reviewReview(c, rating, daysSinceLastReview, reviewDatetime);
            case RELEARNING -> reviewRelearning(c, rating, daysSinceLastReview, reviewDatetime);
        };

        // fuzz 仅作用于进入 Review 态的天级间隔（对齐 py-fsrs 收尾逻辑）
        if (enableFuzzing && c.getState() == FsrsState.REVIEW) {
            intervalSeconds = fuzzIntervalSeconds(intervalSeconds);
        }

        c.setDue(reviewDatetime.plusSeconds(intervalSeconds));
        c.setLastReview(reviewDatetime);

        return new ReviewResult(c, new FsrsReviewLog(c.getCardId(), rating, reviewDatetime, null));
    }

    /** 当前可提取率 R = (1 + FACTOR·elapsed/S)^DECAY，elapsed 向下取整天且不为负 */
    public double retrievability(FsrsCard card, LocalDateTime currentDatetime) {
        if (card.getLastReview() == null || card.getStability() == null) {
            return 0.0;
        }
        long elapsedDays = Math.max(0, Duration.between(card.getLastReview(), currentDatetime).toDays());
        return Math.pow(1.0 + factor * elapsedDays / card.getStability(), decay);
    }

    // ==================== 三态处理 ====================

    /** Learning 态：分钟级短步长，走完最后一步或 Easy 进 Review */
    private long reviewLearning(FsrsCard c, FsrsRating rating, Long daysSince, LocalDateTime now) {
        int step = c.getStep() == null ? 0 : c.getStep();
        updateStabilityAndDifficulty(c, rating, daysSince, now);

        // 边界：步序号已超出当前配置（历史遗留数据），Hard/Good/Easy 直接按 Review 天级排
        if (step >= learningSteps.length && rating != FsrsRating.AGAIN) {
            toReviewState(c);
            return daySeconds(nextIntervalDays(c.getStability()));
        }

        switch (rating) {
            case AGAIN -> {
                c.setStep(0);
                return learningSteps[0];
            }
            case HARD -> {
                // step 停留不动，间隔取半步
                if (step == 0 && learningSteps.length == 1) {
                    return (long) (learningSteps[0] * 1.5);
                } else if (step == 0 && learningSteps.length >= 2) {
                    return (learningSteps[0] + learningSteps[1]) / 2;
                }
                return learningSteps[step];
            }
            case GOOD -> {
                if (step + 1 == learningSteps.length) {
                    // 走完最后一步，进 Review 按天排
                    toReviewState(c);
                    return daySeconds(nextIntervalDays(c.getStability()));
                }
                c.setStep(step + 1);
                return learningSteps[step + 1];
            }
            case EASY -> {
                toReviewState(c);
                return daySeconds(nextIntervalDays(c.getStability()));
            }
        }
        throw new IllegalArgumentException("未知评分: " + rating);
    }

    /** Review 态：天级调度，Again 降级 Relearning */
    private long reviewReview(FsrsCard c, FsrsRating rating, Long daysSince, LocalDateTime now) {
        requireMatureCard(c);
        // 同日复习只走短稳定度，不推进天级稳定度演化
        if (daysSince != null && daysSince < 1) {
            c.setStability(shortTermStability(c.getStability(), rating));
        } else {
            c.setStability(nextStability(c.getDifficulty(), c.getStability(), retrievability(c, now), rating));
        }
        c.setDifficulty(nextDifficulty(c.getDifficulty(), rating));

        if (rating == FsrsRating.AGAIN) {
            if (relearningSteps.length == 0) {
                // 未配置重学步长时保持 Review 态直接排天
                return daySeconds(nextIntervalDays(c.getStability()));
            }
            c.setState(FsrsState.RELEARNING);
            c.setStep(0);
            return relearningSteps[0];
        }
        return daySeconds(nextIntervalDays(c.getStability()));
    }

    /** Relearning 态：镜像 Learning，走完最后一步回 Review */
    private long reviewRelearning(FsrsCard c, FsrsRating rating, Long daysSince, LocalDateTime now) {
        requireMatureCard(c);
        int step = c.getStep() == null ? 0 : c.getStep();
        updateStabilityAndDifficulty(c, rating, daysSince, now);

        if (step >= relearningSteps.length && rating != FsrsRating.AGAIN) {
            toReviewState(c);
            return daySeconds(nextIntervalDays(c.getStability()));
        }

        switch (rating) {
            case AGAIN -> {
                c.setStep(0);
                return relearningSteps[0];
            }
            case HARD -> {
                if (step == 0 && relearningSteps.length == 1) {
                    return (long) (relearningSteps[0] * 1.5);
                } else if (step == 0 && relearningSteps.length >= 2) {
                    return (relearningSteps[0] + relearningSteps[1]) / 2;
                }
                return relearningSteps[step];
            }
            case GOOD -> {
                if (step + 1 == relearningSteps.length) {
                    toReviewState(c);
                    return daySeconds(nextIntervalDays(c.getStability()));
                }
                c.setStep(step + 1);
                return relearningSteps[step + 1];
            }
            case EASY -> {
                toReviewState(c);
                return daySeconds(nextIntervalDays(c.getStability()));
            }
        }
        throw new IllegalArgumentException("未知评分: " + rating);
    }

    // ==================== 公式族（对齐 py-fsrs 私有方法） ====================

    /** 按“是否同日”分派稳定度/难度更新：Learning/Relearning 态共用 */
    private void updateStabilityAndDifficulty(FsrsCard c, FsrsRating rating, Long daysSince, LocalDateTime now) {
        if (c.getStability() == null || c.getDifficulty() == null) {
            // 首次复习：初始稳定度/难度
            c.setStability(initialStability(rating));
            c.setDifficulty(initialDifficulty(rating));
        } else if (daysSince != null && daysSince < 1) {
            // 同日复习：短稳定度
            c.setStability(shortTermStability(c.getStability(), rating));
            c.setDifficulty(nextDifficulty(c.getDifficulty(), rating));
        } else {
            c.setStability(nextStability(c.getDifficulty(), c.getStability(), retrievability(c, now), rating));
            c.setDifficulty(nextDifficulty(c.getDifficulty(), rating));
        }
    }

    /** S0 = w[rating-1]，下限 STABILITY_MIN */
    private double initialStability(FsrsRating rating) {
        return Math.max(parameters[rating.getCode() - 1], STABILITY_MIN);
    }

    /** D0 = w4 - e^(w5·(rating-1)) + 1，clamp [1,10] */
    private double initialDifficulty(FsrsRating rating) {
        double d = parameters[4] - Math.exp(parameters[5] * (rating.getCode() - 1)) + 1.0;
        return clampDifficulty(d);
    }

    /**
     * 难度更新：线性阻尼 + 均值回归（越难涨得越慢，且向 Easy 初始难度缓慢回归），clamp [1,10]。
     */
    private double nextDifficulty(double difficulty, FsrsRating rating) {
        double deltaDifficulty = -(parameters[6] * (rating.getCode() - 3));
        double linearDamping = (10.0 - difficulty) * deltaDifficulty / 9.0;
        double arg1 = initialDifficulty(FsrsRating.EASY);
        double arg2 = difficulty + linearDamping;
        double reverted = parameters[7] * arg1 + (1 - parameters[7]) * arg2;
        return clampDifficulty(reverted);
    }

    /** 稳定度更新分派：Again 走遗忘公式，其余走回忆公式，下限 STABILITY_MIN */
    private double nextStability(double difficulty, double stability, double retrievability, FsrsRating rating) {
        double next = rating == FsrsRating.AGAIN
                ? nextForgetStability(difficulty, stability, retrievability)
                : nextRecallStability(difficulty, stability, retrievability, rating);
        return Math.max(next, STABILITY_MIN);
    }

    /** 遗忘后的新稳定度：长期项与短期项取小 */
    private double nextForgetStability(double difficulty, double stability, double retrievability) {
        double longTerm = parameters[11]
                * Math.pow(difficulty, -parameters[12])
                * (Math.pow(stability + 1, parameters[13]) - 1)
                * Math.exp((1 - retrievability) * parameters[14]);
        double shortTerm = stability / Math.exp(parameters[17] * parameters[18]);
        return Math.min(longTerm, shortTerm);
    }

    /** 记住后的新稳定度：Hard 惩罚 w15、Easy 奖励 w16 */
    private double nextRecallStability(double difficulty, double stability, double retrievability, FsrsRating rating) {
        double hardPenalty = rating == FsrsRating.HARD ? parameters[15] : 1.0;
        double easyBonus = rating == FsrsRating.EASY ? parameters[16] : 1.0;
        return stability * (1.0
                + Math.exp(parameters[8])
                * (11 - difficulty)
                * Math.pow(stability, -parameters[9])
                * (Math.exp((1 - retrievability) * parameters[10]) - 1)
                * hardPenalty
                * easyBonus);
    }

    /**
     * 短稳定度（同日复习）：e^(w17·(rating-3+w18))·S^(-w19)；
     * Hard/Good/Easy 的增长因子下限 1.0（同日记住不缩稳定度），Again 允许缩。
     */
    private double shortTermStability(double stability, FsrsRating rating) {
        double increase = Math.exp(parameters[17] * (rating.getCode() - 3 + parameters[18]))
                * Math.pow(stability, -parameters[19]);
        if (rating != FsrsRating.AGAIN) {
            increase = Math.max(increase, 1.0);
        }
        return Math.max(stability * increase, STABILITY_MIN);
    }

    /**
     * 由目标保持率反推下次间隔（天）：(S/FACTOR)·(retention^(1/DECAY) - 1)，
     * round 到整天（银行家舍入，对齐 Python round）后 clamp [1, maximumInterval]。
     */
    private int nextIntervalDays(double stability) {
        double raw = (stability / factor) * (Math.pow(desiredRetention, 1.0 / decay) - 1.0);
        long days = (long) Math.rint(raw);
        return (int) Math.max(1, Math.min(days, maximumInterval));
    }

    // ==================== 内部工具 ====================

    private void toReviewState(FsrsCard c) {
        c.setState(FsrsState.REVIEW);
        c.setStep(null);
    }

    private void requireMatureCard(FsrsCard c) {
        if (c.getStability() == null || c.getDifficulty() == null) {
            throw new IllegalArgumentException("Review/Relearning 态卡片的稳定度与难度不可为空");
        }
    }

    private long daySeconds(int days) {
        return days * SECONDS_PER_DAY;
    }

    private double clampDifficulty(double difficulty) {
        return Math.min(Math.max(difficulty, MIN_DIFFICULTY), MAX_DIFFICULTY);
    }

    /** 对 Review 态天级间隔加随机抖动，打散到期密度（对齐 py-fsrs _get_fuzzed_interval） */
    private long fuzzIntervalSeconds(long intervalSeconds) {
        long intervalDays = intervalSeconds / SECONDS_PER_DAY;
        if (intervalDays < 2.5) {
            return intervalSeconds;
        }
        double delta = 1.0;
        for (double[] range : FUZZ_RANGES) {
            delta += range[2] * Math.max(Math.min((double) intervalDays, range[1]) - range[0], 0.0);
        }
        long minIvl = (long) Math.rint(intervalDays - delta);
        long maxIvl = (long) Math.rint(intervalDays + delta);
        minIvl = Math.max(2, minIvl);
        maxIvl = Math.min(maxIvl, maximumInterval);
        minIvl = Math.min(minIvl, maxIvl);
        long fuzzedDays = (long) (random.nextDouble() * (maxIvl - minIvl + 1)) + minIvl;
        return Math.min(fuzzedDays, maximumInterval) * SECONDS_PER_DAY;
    }
}
