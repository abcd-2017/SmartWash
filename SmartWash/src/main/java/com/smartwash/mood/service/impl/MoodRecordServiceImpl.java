package com.smartwash.mood.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.mood.entity.MoodRecord;
import com.smartwash.mood.from.SaveMoodFrom;
import com.smartwash.mood.mapper.MoodRecordMapper;
import com.smartwash.mood.service.MoodRecordService;
import com.smartwash.mood.vo.MoodCalendarDayVo;
import com.smartwash.mood.vo.MoodLevelCountVo;
import com.smartwash.mood.vo.MoodStatsVo;
import com.smartwash.mood.vo.MoodRecordVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 情绪气象站服务实现。
 * 写路径：INSERT ... ON DUPLICATE KEY UPDATE（uk_user_date 原子裁决），单条 SQL 自带原子性，不套外层事务；
 * 读路径：所有查询强制 user_id = 当前登录用户，月区间按自然月闭区间 [1日, 月末]。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MoodRecordServiceImpl extends ServiceImpl<MoodRecordMapper, MoodRecord>
        implements MoodRecordService {

    /** 情绪等级上下界（与 DB CHECK 一致：1暴雨 2阴 3多云 4晴 5大晴） */
    private static final int LEVEL_MIN = 1;
    private static final int LEVEL_MAX = 5;
    /** ym 参数格式：yyyy-MM */
    private static final Pattern YM_PATTERN = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    /** 月均值保留小数位 */
    private static final int AVERAGE_SCALE = 2;

    // ==================== 写入（upsert：唯一索引原子裁决） ====================

    @Override
    public MoodRecordVo saveToday(SaveMoodFrom from, Long userId) {
        // 控制器 @Valid 之外的服务端兜底校验，DB CHECK 是最终防线
        if (from.getLevel() == null || from.getLevel() < LEVEL_MIN || from.getLevel() > LEVEL_MAX) {
            throw new CustomExceptions("情绪等级须为1..5");
        }
        if (StrUtil.isBlank(from.getColor())) {
            throw new CustomExceptions("颜色不能为空");
        }
        // 空白词/备注归一为 NULL，保持「可空」语义干净
        String word = StrUtil.trimToNull(from.getWord());
        String note = StrUtil.trimToNull(from.getNote());
        String color = from.getColor().trim();
        LocalDate today = LocalDate.now();

        // 单条 upsert 自带原子性：uk_user_date 冲突即转 UPDATE 覆盖，禁止先查后分叉
        this.baseMapper.upsertToday(userId, today, from.getLevel(), color, word, note);
        MoodRecord record = this.getBaseMapper().selectOne(new LambdaQueryWrapper<MoodRecord>()
                .eq(MoodRecord::getUserId, userId)
                .eq(MoodRecord::getMoodDate, today));
        if (record == null) {
            throw new CustomExceptions("情绪记录保存异常，请重试");
        }
        log.info("情绪记录已保存, userId: {}, date: {}, level: {}", userId, today, from.getLevel());
        return buildRecordVo(record);
    }

    // ==================== 月历 ====================

    @Override
    public List<MoodCalendarDayVo> monthCalendar(String ym, Long userId) {
        YearMonth month = parseYm(ym);
        List<MoodRecord> records = this.baseMapper.selectMonthDays(
                userId, month.atDay(1), month.atEndOfMonth());
        return records.stream()
                .map(record -> new MoodCalendarDayVo()
                        .setDate(record.getMoodDate().format(ISO_DATE))
                        .setLevel(record.getMoodLevel())
                        .setColor(record.getColor())
                        .setWord(record.getWord()))
                .collect(Collectors.toList());
    }

    // ==================== 趋势统计 ====================

    @Override
    public MoodStatsVo monthStats(String ym, Long userId) {
        YearMonth month = parseYm(ym);
        List<MoodLevelCountVo> distribution = this.baseMapper.selectLevelCounts(
                userId, month.atDay(1), month.atEndOfMonth());
        long recordedDays = distribution.stream()
                .mapToLong(MoodLevelCountVo::getRecordCount)
                .sum();
        return new MoodStatsVo()
                .setYm(month.toString())
                .setRecordedDays(recordedDays)
                .setAverageLevel(averageLevel(distribution, recordedDays))
                .setDistribution(distribution);
    }

    // ==================== 内部方法 ====================

    /** ym 解析：非法格式直接业务报错（空月/跨月为合法值，自然返回空结果） */
    private YearMonth parseYm(String ym) {
        if (StrUtil.isBlank(ym) || !YM_PATTERN.matcher(ym.trim()).matches()) {
            throw new CustomExceptions("ym 参数格式须为 yyyy-MM，例如 2026-10");
        }
        return YearMonth.parse(ym.trim());
    }

    /** 等级均值：Σ(level*count)/Σcount，HALF_UP 保留2位；无记录返回 null */
    private Double averageLevel(List<MoodLevelCountVo> distribution, long recordedDays) {
        if (recordedDays == 0) {
            return null;
        }
        BigDecimal weightedSum = distribution.stream()
                .map(item -> BigDecimal.valueOf(item.getLevel())
                        .multiply(BigDecimal.valueOf(item.getRecordCount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return weightedSum
                .divide(BigDecimal.valueOf(recordedDays), AVERAGE_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private MoodRecordVo buildRecordVo(MoodRecord record) {
        return new MoodRecordVo()
                .setDate(record.getMoodDate().format(ISO_DATE))
                .setLevel(record.getMoodLevel())
                .setColor(record.getColor())
                .setWord(record.getWord())
                .setNote(record.getNote())
                .setUpdatedAt(record.getUpdatedAt());
    }
}
