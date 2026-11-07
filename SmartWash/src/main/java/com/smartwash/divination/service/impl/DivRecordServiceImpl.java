package com.smartwash.divination.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.divination.core.DivinationCore;
import com.smartwash.divination.entity.DivInterpretation;
import com.smartwash.divination.entity.DivRecord;
import com.smartwash.divination.entity.DivFeedback;
import com.smartwash.divination.mapper.DivFeedbackMapper;
import com.smartwash.divination.from.CreateRecordFrom;
import com.smartwash.divination.from.DivRecordSearchFrom;
import com.smartwash.divination.from.FeedbackFrom;
import com.smartwash.divination.from.SearchRecordFrom;
import com.smartwash.divination.mapper.DivInterpretationMapper;
import com.smartwash.divination.mapper.DivRecordMapper;
import com.smartwash.divination.service.DivNudgeService;
import com.smartwash.divination.service.IDivRecordService;
import com.smartwash.divination.vo.DivFeedbackAggVo;
import com.smartwash.divination.vo.DivMonthCountVo;
import com.smartwash.divination.vo.DivNameCountVo;
import com.smartwash.divination.vo.DivNudgeVo;
import com.smartwash.divination.vo.DivRecordSearchVo;
import com.smartwash.divination.vo.DivStatsVo;
import com.smartwash.divination.vo.InterpretationVo;
import com.smartwash.divination.vo.RecordDetailVo;
import com.smartwash.divination.vo.RecordVo;
import com.smartwash.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 卦例服务实现：创建（服务端 core 重算校验）/卦历分页/详情/今日一签。
 * 占卜闭环新增：解读历史检索 / 应验回访提醒 / 盘感统计（均为追加方法，既有方法逻辑不动）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DivRecordServiceImpl extends ServiceImpl<DivRecordMapper, DivRecord> implements IDivRecordService {

    /** 盘感统计月度趋势窗口：近 12 个自然月（含当月） */
    private static final int MONTH_TREND_MONTHS = 12;

    private final DivinationCore divinationCore;
    private final DivInterpretationMapper interpretationMapper;
    private final DivFeedbackMapper feedbackMapper;
    private final DivNudgeService nudgeService;

    @Override
    public Long createRecord(CreateRecordFrom from, Long userId) {
        // 1. 服务端 core 重算权威盘面
        String serverChart = divinationCore.recalculate(
                from.getMethod(),
                from.getCastMethod(),
                from.getCastAt(),
                from.getTzOffset() != null ? from.getTzOffset() : 480,
                from.getLines()
        );

        // 2. 比对客户端盘面与服务端盘面
        boolean verified = divinationCore.verify(from.getClientChart(), serverChart);

        // 3. 构建卦例实体
        DivRecord record = new DivRecord();
        record.setUserId(userId);
        record.setMethod(from.getMethod());
        record.setCategory(StringUtils.hasText(from.getCategory()) ? from.getCategory() : "general");
        record.setQuestion(from.getQuestion());
        record.setCastMethod(from.getCastMethod());
        record.setCastAt(LocalDateTime.ofEpochSecond(from.getCastAt() / 1000, 0,
                java.time.ZoneOffset.ofTotalSeconds((from.getTzOffset() != null ? from.getTzOffset() : 480) * 60)));
        record.setTzOffset(from.getTzOffset() != null ? from.getTzOffset() : 480);
        record.setSource("app");
        record.setLines(from.getLines() != null ? JsonUtils.toJson(from.getLines()) : null);
        record.setClientChart(from.getClientChart());
        record.setServerChart(serverChart);
        record.setChartVerified(verified ? 1 : 0);

        save(record);
        log.info("创建卦例成功, userId: {}, recordId: {}, method: {}, verified: {}",
                userId, record.getId(), from.getMethod(), verified);
        return record.getId();
    }

    @Override
    public Page<RecordVo> searchRecords(SearchRecordFrom from, Long userId) {
        Page<DivRecord> page = new Page<>(from.getPage(), from.getSize());
        LambdaQueryWrapper<DivRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DivRecord::getUserId, userId);
        wrapper.and(StringUtils.hasText(from.getMethod()), w -> w.eq(DivRecord::getMethod, from.getMethod()));
        if (StringUtils.hasText(from.getDateFrom())) {
            wrapper.ge(DivRecord::getCreatedAt, LocalDate.parse(from.getDateFrom()).atStartOfDay());
        }
        if (StringUtils.hasText(from.getDateTo())) {
            wrapper.le(DivRecord::getCreatedAt, LocalDate.parse(from.getDateTo()).plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(DivRecord::getCreatedAt);

        Page<DivRecord> recordPage = page(page, wrapper);
        Page<RecordVo> voPage = new Page<>();
        BeanUtils.copyProperties(recordPage, voPage);

        List<RecordVo> records = new ArrayList<>();
        for (DivRecord record : recordPage.getRecords()) {
            RecordVo vo = new RecordVo();
            BeanUtils.copyProperties(record, vo);
            // 填充最近一次解读摘要
            vo.setLatestInterpretation(null);
            records.add(vo);
        }
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public RecordDetailVo getRecordDetail(Long id, Long userId) {
        DivRecord record = getById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            return null;
        }
        RecordDetailVo vo = new RecordDetailVo();
        BeanUtils.copyProperties(record, vo);

        // 查询最近一次解读
        LambdaQueryWrapper<DivInterpretation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DivInterpretation::getRecordId, id);
        wrapper.orderByDesc(DivInterpretation::getCreatedAt);
        wrapper.last("LIMIT 1");
        DivInterpretation latest = interpretationMapper.selectOne(wrapper);
        if (latest != null) {
            InterpretationVo interpretVo = new InterpretationVo();
            BeanUtils.copyProperties(latest, interpretVo);
            vo.setLatestInterpretation(interpretVo);
        }
        return vo;
    }

    @Override
    public RecordDetailVo getTodayRecord(Long userId) {
        // 今日一签：method=meihua, source=today，同一天复用
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().plusDays(1).atStartOfDay();
        LambdaQueryWrapper<DivRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DivRecord::getUserId, userId);
        wrapper.eq(DivRecord::getMethod, "meihua");
        wrapper.eq(DivRecord::getSource, "today");
        wrapper.ge(DivRecord::getCreatedAt, startOfDay);
        wrapper.lt(DivRecord::getCreatedAt, endOfDay);
        wrapper.orderByDesc(DivRecord::getCreatedAt);
        wrapper.last("LIMIT 1");

        DivRecord todayRecord = getOne(wrapper);
        if (todayRecord == null) {
            // 服务端生成今日时间卦
            long now = System.currentTimeMillis();
            String serverChart = divinationCore.recalculate("meihua", "time", now, 480, null);

            DivRecord record = new DivRecord();
            record.setUserId(userId);
            record.setMethod("meihua");
            record.setCategory("general");
            record.setQuestion("今日运势");
            record.setCastMethod("time");
            record.setCastAt(LocalDateTime.now());
            record.setTzOffset(480);
            record.setSource("today");
            record.setServerChart(serverChart);
            record.setChartVerified(1);
            save(record);
            todayRecord = record;
            log.info("生成今日一签, userId: {}, recordId: {}", userId, record.getId());
        }

        RecordDetailVo vo = new RecordDetailVo();
        BeanUtils.copyProperties(todayRecord, vo);
        return vo;
    }

    @Override
    public void addFeedback(Long recordId, FeedbackFrom from, Long userId) {
        DivRecord record = getById(recordId);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new com.smartwash.exception.CustomExceptions("卦例不存在");
        }
        DivFeedback feedback = new DivFeedback();
        feedback.setRecordId(recordId);
        feedback.setInterpretationId(from.getInterpretationId());
        feedback.setUserId(userId);
        feedback.setRating(from.getRating());
        feedback.setOutcome(from.getOutcome());
        feedback.setOutcomeNote(from.getOutcomeNote());
        feedbackMapper.insert(feedback);
        log.info("反馈已提交, recordId: {}, interpretationId: {}, rating: {}",
                recordId, from.getInterpretationId(), from.getRating());

        // 占卜闭环接线（方法末尾最小侵入追加，不改原有行为）：提交反馈即视为该解读已回访，
        // 将同 interpretation_id 的待回访提醒置为已回访；失败仅告警，不影响反馈主流程
        try {
            int closed = nudgeService.closePendingNudges(from.getInterpretationId(), userId);
            if (closed > 0) {
                log.debug("待回访提醒已关闭, interpretationId: {}, count: {}", from.getInterpretationId(), closed);
            }
        } catch (Exception e) {
            log.warn("关闭待回访提醒失败(不影响反馈), recordId: {}, interpretationId: {}",
                    recordId, from.getInterpretationId(), e);
        }
    }

    // ==================== 占卜闭环新增方法（只增不改） ====================

    @Override
    public Page<DivRecordSearchVo> searchRecordsByKeyword(DivRecordSearchFrom from, Long userId) {
        Page<DivRecordSearchVo> page = new Page<>(from.getPage(), from.getSize());
        // 契约：无 keyword 返回空页（前端不空调用）
        if (!StringUtils.hasText(from.getKeyword())) {
            return page;
        }
        return baseMapper.searchByKeyword(page, userId, from.getKeyword().trim());
    }

    @Override
    public Page<DivNudgeVo> pageNudges(Integer page, Integer size, Long userId) {
        return nudgeService.pageNudges(page, size, userId);
    }

    @Override
    public boolean ignoreNudge(Long id, Long userId) {
        return nudgeService.ignoreNudge(id, userId);
    }

    @Override
    public Integer countPendingNudges(Long userId) {
        return Math.toIntExact(nudgeService.countPending(userId));
    }

    @Override
    public DivStatsVo getDivStats(Long userId) {
        DivStatsVo vo = new DivStatsVo();

        // 卦例总数
        LambdaQueryWrapper<DivRecord> totalWrapper = new LambdaQueryWrapper<>();
        totalWrapper.eq(DivRecord::getUserId, userId);
        vo.setTotalRecords(count(totalWrapper));

        // 维度计数
        vo.setMethodCounts(baseMapper.selectMethodCounts(userId));
        vo.setCategoryCounts(baseMapper.selectCategoryCounts(userId));

        // 近 12 个自然月趋势（缺失月份补 0，保证端上曲线连续）
        YearMonth current = YearMonth.now();
        YearMonth startMonth = current.minusMonths(MONTH_TREND_MONTHS - 1L);
        List<DivMonthCountVo> trend = new ArrayList<>();
        Map<String, Long> byMonth = new HashMap<>();
        for (DivMonthCountVo row : baseMapper.selectMonthTrend(userId, startMonth.atDay(1).atStartOfDay())) {
            byMonth.put(row.getMonth(), row.getCount() == null ? 0L : row.getCount());
        }
        for (int i = 0; i < MONTH_TREND_MONTHS; i++) {
            String month = startMonth.plusMonths(i).toString();
            DivMonthCountVo item = new DivMonthCountVo();
            item.setMonth(month);
            item.setCount(byMonth.getOrDefault(month, 0L));
            trend.add(item);
        }
        vo.setMonthTrend(trend);

        // 反馈聚合：口径 verify = rating ≥ 4（5 档评分，≥4 记应验）；无反馈时 verifyRate = 0
        DivFeedbackAggVo agg = feedbackMapper.selectUserFeedbackAgg(userId);
        long feedbackTotal = agg == null || agg.getFeedbackTotal() == null ? 0L : agg.getFeedbackTotal();
        long verifyCount = agg == null || agg.getVerifyCount() == null ? 0L : agg.getVerifyCount();
        vo.setFeedbackTotal(feedbackTotal);
        vo.setVerifyCount(verifyCount);
        if (feedbackTotal > 0) {
            vo.setVerifyRate(BigDecimal.valueOf(verifyCount)
                    .divide(BigDecimal.valueOf(feedbackTotal), 4, RoundingMode.HALF_UP));
        } else {
            vo.setVerifyRate(BigDecimal.ZERO);
        }
        return vo;
    }
}
