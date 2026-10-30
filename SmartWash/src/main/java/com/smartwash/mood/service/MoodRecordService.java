package com.smartwash.mood.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.mood.entity.MoodRecord;
import com.smartwash.mood.from.SaveMoodFrom;
import com.smartwash.mood.vo.MoodCalendarDayVo;
import com.smartwash.mood.vo.MoodStatsVo;
import com.smartwash.mood.vo.MoodRecordVo;

import java.util.List;

/**
 * 情绪气象站服务。
 * 并发安全说明：写入统一走 INSERT ... ON DUPLICATE KEY UPDATE，
 * 由 uk_user_date 唯一索引原子裁决，禁止「先查后分叉」（TOCTOU 竞态窗口）。
 */
public interface MoodRecordService extends IService<MoodRecord> {

    /** 记录/覆盖今日情绪（upsert，同日重复提交即覆盖） */
    MoodRecordVo saveToday(SaveMoodFrom from, Long userId);

    /** 月历热图数据：仅返回当月有记录的日期（无记录日期端上缺省灰格），跨月/空月返回空数组 */
    List<MoodCalendarDayVo> monthCalendar(String ym, Long userId);

    /** 月度趋势统计：各等级计数 + 记录天数 + 月均值（等级均值保留2位） */
    MoodStatsVo monthStats(String ym, Long userId);
}
