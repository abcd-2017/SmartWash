package com.smartwash.divination.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.divination.core.DivinationCore;
import com.smartwash.divination.entity.DivInterpretation;
import com.smartwash.divination.entity.DivRecord;
import com.smartwash.divination.entity.DivFeedback;
import com.smartwash.divination.mapper.DivFeedbackMapper;
import com.smartwash.divination.from.CreateRecordFrom;
import com.smartwash.divination.from.FeedbackFrom;
import com.smartwash.divination.from.SearchRecordFrom;
import com.smartwash.divination.mapper.DivInterpretationMapper;
import com.smartwash.divination.mapper.DivRecordMapper;
import com.smartwash.divination.service.IDivRecordService;
import com.smartwash.divination.vo.InterpretationVo;
import com.smartwash.divination.vo.RecordDetailVo;
import com.smartwash.divination.vo.RecordVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 卦例服务实现：创建（服务端 core 重算校验）/卦历分页/详情/今日一签。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DivRecordServiceImpl extends ServiceImpl<DivRecordMapper, DivRecord> implements IDivRecordService {

    private final DivinationCore divinationCore;
    private final DivInterpretationMapper interpretationMapper;
    private final DivFeedbackMapper feedbackMapper;

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
        record.setLines(from.getLines() != null ? JSON.toJSONString(from.getLines()) : null);
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
    }
}
