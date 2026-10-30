package com.smartwash.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.review.entity.ReviewCard;
import com.smartwash.review.entity.ReviewLog;
import com.smartwash.review.entity.StudyMaterial;
import com.smartwash.review.from.AddCardFrom;
import com.smartwash.review.from.AnswerFrom;
import com.smartwash.review.mapper.ReviewCardMapper;
import com.smartwash.review.mapper.ReviewLogMapper;
import com.smartwash.review.mapper.StudyMaterialMapper;
import com.smartwash.review.scheduler.FsrsCard;
import com.smartwash.review.scheduler.FsrsRating;
import com.smartwash.review.scheduler.FsrsReviewLog;
import com.smartwash.review.scheduler.FsrsScheduler;
import com.smartwash.review.scheduler.FsrsState;
import com.smartwash.review.service.IReviewCardService;
import com.smartwash.review.vo.ReviewCardVo;
import com.smartwash.review.vo.ReviewQueueVo;
import com.smartwash.review.vo.ReviewStatsVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 复习卡服务实现：建卡 / 分页 / 今日队列 / 评分调度 / 统计。
 * 单人无并发写（同一用户不会同时答同一张卡），评分走 @Transactional 双写即可。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewCardServiceImpl extends ServiceImpl<ReviewCardMapper, ReviewCard>
        implements IReviewCardService {

    /** 新卡每日注入配额（每次队列最多带出的未学新卡数） */
    private static final int DAILY_NEW_LIMIT = 20;

    /** 到期卡单次拉取上限 */
    private static final int DUE_LIMIT = 50;

    /** 统计回看天数 */
    private static final int STATS_DAYS = 30;

    /** FSRS 调度器（生产构造：默认权重 + fuzz 开启） */
    private final FsrsScheduler scheduler = new FsrsScheduler();

    private final ReviewLogMapper reviewLogMapper;
    private final StudyMaterialMapper studyMaterialMapper;

    @Override
    public Long addCard(Long materialId, AddCardFrom from, Long userId) {
        if (materialId != null) {
            StudyMaterial material = studyMaterialMapper.selectById(materialId);
            if (material == null || !material.getUserId().equals(userId)) {
                throw new CustomExceptions("资料不存在");
            }
        }
        // 初始 FSRS 态：state=Learning, step=0, due=now，从未复习
        LocalDateTime now = LocalDateTime.now();
        ReviewCard card = new ReviewCard();
        card.setUserId(userId);
        card.setMaterialId(materialId);
        card.setQuestion(from.getQuestion());
        card.setAnswer(from.getAnswer());
        card.setState(FsrsState.LEARNING.getCode());
        card.setStep(0);
        card.setStability(null);
        card.setDifficulty(null);
        card.setDueAt(now);
        card.setLastReviewAt(null);
        card.setReps(0);
        card.setLapses(0);
        save(card);
        log.info("创建复习卡成功, userId: {}, materialId: {}, cardId: {}", userId, materialId, card.getId());
        return card.getId();
    }

    @Override
    public Page<ReviewCardVo> pageCards(Long materialId, Integer page, Integer size, Long userId) {
        Page<ReviewCard> resultPage = new Page<>(page, size);
        LambdaQueryWrapper<ReviewCard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReviewCard::getUserId, userId);
        wrapper.eq(ReviewCard::getMaterialId, materialId);
        wrapper.orderByAsc(ReviewCard::getId);

        Page<ReviewCard> cardPage = page(resultPage, wrapper);
        Page<ReviewCardVo> voPage = new Page<>();
        BeanUtils.copyProperties(cardPage, voPage);
        List<ReviewCardVo> vos = new ArrayList<>();
        for (ReviewCard card : cardPage.getRecords()) {
            vos.add(toVo(card));
        }
        voPage.setRecords(vos);
        return voPage;
    }

    @Override
    public ReviewQueueVo getQueue(Long userId) {
        LocalDateTime now = LocalDateTime.now();

        // 到期卡：due<=now 且已复习过，Learning/Relearning 分钟级在前、Review 天级在后
        LambdaQueryWrapper<ReviewCard> dueWrapper = new LambdaQueryWrapper<>();
        dueWrapper.eq(ReviewCard::getUserId, userId);
        dueWrapper.le(ReviewCard::getDueAt, now);
        dueWrapper.isNotNull(ReviewCard::getLastReviewAt);
        dueWrapper.orderByAsc(ReviewCard::getState);
        dueWrapper.orderByAsc(ReviewCard::getDueAt);
        dueWrapper.last("LIMIT " + DUE_LIMIT);
        List<ReviewCard> dueCards = list(dueWrapper);

        // 新卡注入队尾：从未复习，按建卡先后，配额 20
        LambdaQueryWrapper<ReviewCard> newWrapper = new LambdaQueryWrapper<>();
        newWrapper.eq(ReviewCard::getUserId, userId);
        newWrapper.isNull(ReviewCard::getLastReviewAt);
        newWrapper.orderByAsc(ReviewCard::getCreatedAt);
        newWrapper.last("LIMIT " + DAILY_NEW_LIMIT);
        List<ReviewCard> newCards = list(newWrapper);

        List<ReviewCardVo> cards = new ArrayList<>(dueCards.size() + newCards.size());
        dueCards.forEach(card -> cards.add(toVo(card)));
        newCards.forEach(card -> cards.add(toVo(card)));

        ReviewQueueVo vo = new ReviewQueueVo();
        vo.setDueCount(dueCards.size());
        vo.setNewCount(newCards.size());
        vo.setCards(cards);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewCardVo answer(AnswerFrom from, Long userId) {
        // 评分越界防御（枚举还原失败即拒绝）
        FsrsRating rating = FsrsRating.of(from.getRating());

        ReviewCard card = getById(from.getCardId());
        if (card == null) {
            throw new CustomExceptions("卡片不存在");
        }
        if (!card.getUserId().equals(userId)) {
            // 归属校验，防横向越权
            throw new CustomExceptions("无权操作该卡片");
        }

        // 纯函数重算：不污染原实体
        LocalDateTime now = LocalDateTime.now();
        FsrsCard fsrsCard = toFsrsCard(card);
        FsrsScheduler.ReviewResult result = scheduler.reviewCard(fsrsCard, rating, now);
        FsrsCard updated = result.card();
        FsrsReviewLog fsrsLog = result.log();

        // 更新卡片 FSRS 状态
        card.setState(updated.getState().getCode());
        card.setStep(updated.getStep());
        card.setStability(updated.getStability() == null ? null : BigDecimal.valueOf(updated.getStability())
                .setScale(4, java.math.RoundingMode.HALF_UP));
        card.setDifficulty(updated.getDifficulty() == null ? null : BigDecimal.valueOf(updated.getDifficulty())
                .setScale(4, java.math.RoundingMode.HALF_UP));
        card.setDueAt(updated.getDue());
        card.setLastReviewAt(updated.getLastReview());
        card.setReps(card.getReps() == null ? 1 : card.getReps() + 1);
        if (rating == FsrsRating.AGAIN) {
            card.setLapses(card.getLapses() == null ? 1 : card.getLapses() + 1);
        }
        updateById(card);

        // 写复习日志（重放重算 + 统计依赖）
        ReviewLog logEntity = new ReviewLog();
        logEntity.setUserId(userId);
        logEntity.setCardId(card.getId());
        logEntity.setRating(fsrsLog.getRating().getCode());
        logEntity.setReviewedAt(fsrsLog.getReviewedAt());
        logEntity.setDurationMs(from.getDurationMs());
        reviewLogMapper.insert(logEntity);

        log.debug("复习评分完成, userId: {}, cardId: {}, rating: {}, state: {}, dueAt: {}",
                userId, card.getId(), rating, card.getState(), card.getDueAt());
        return toVo(card);
    }

    @Override
    public ReviewStatsVo stats(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(STATS_DAYS - 1L).atStartOfDay();

        LambdaQueryWrapper<ReviewLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReviewLog::getUserId, userId);
        wrapper.ge(ReviewLog::getReviewedAt, start);
        List<ReviewLog> logs = reviewLogMapper.selectList(wrapper);

        // 近 30 天每日复习数（缺日补零）
        Map<LocalDate, Long> dailyMap = new LinkedHashMap<>();
        for (int i = 0; i < STATS_DAYS; i++) {
            dailyMap.put(today.minusDays(STATS_DAYS - 1L - i), 0L);
        }
        Map<FsrsRating, Long> ratingMap = new EnumMap<>(FsrsRating.class);
        for (FsrsRating rating : FsrsRating.values()) {
            ratingMap.put(rating, 0L);
        }
        for (ReviewLog logEntity : logs) {
            LocalDate day = logEntity.getReviewedAt().toLocalDate();
            dailyMap.computeIfPresent(day, (k, v) -> v + 1);
            FsrsRating rating = FsrsRating.of(logEntity.getRating());
            ratingMap.merge(rating, 1L, Long::sum);
        }

        ReviewStatsVo vo = new ReviewStatsVo();
        List<ReviewStatsVo.DailyCount> daily = new ArrayList<>(STATS_DAYS);
        dailyMap.forEach((date, count) -> daily.add(new ReviewStatsVo.DailyCount(date, count)));
        vo.setDaily(daily);

        List<ReviewStatsVo.RatingShare> shares = new ArrayList<>(4);
        ratingMap.forEach((rating, count) -> shares.add(new ReviewStatsVo.RatingShare(rating.getCode(), count)));
        vo.setRatingShare(shares);
        return vo;
    }

    // ==================== 私有转换 ====================

    /** 实体 → 调度器内存卡（字段名对齐 py-fsrs Card） */
    private FsrsCard toFsrsCard(ReviewCard card) {
        return new FsrsCard(
                card.getId(),
                FsrsState.of(card.getState()),
                card.getStep(),
                card.getStability() == null ? null : card.getStability().doubleValue(),
                card.getDifficulty() == null ? null : card.getDifficulty().doubleValue(),
                card.getDueAt(),
                card.getLastReviewAt()
        );
    }

    private ReviewCardVo toVo(ReviewCard card) {
        ReviewCardVo vo = new ReviewCardVo();
        vo.setId(card.getId());
        vo.setMaterialId(card.getMaterialId());
        vo.setQuestion(card.getQuestion());
        vo.setAnswer(card.getAnswer());
        vo.setState(card.getState());
        vo.setStep(card.getStep());
        vo.setStability(card.getStability());
        vo.setDifficulty(card.getDifficulty());
        vo.setDueAt(card.getDueAt());
        vo.setLastReviewAt(card.getLastReviewAt());
        vo.setReps(card.getReps());
        vo.setLapses(card.getLapses());
        vo.setCreatedAt(card.getCreatedAt());
        return vo;
    }
}
