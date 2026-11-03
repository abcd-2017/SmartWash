package com.smartwash.mood.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.mood.entity.MoodRecord;
import com.smartwash.mood.vo.MoodLevelCountVo;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 情绪记录 Mapper。
 * upsert 走 INSERT ... ON DUPLICATE KEY UPDATE（uk_user_date 冲突原子转 UPDATE，
 * 规避「先查后分叉」的 TOCTOU 竞态窗口）。
 */
public interface MoodRecordMapper extends BaseMapper<MoodRecord> {

    /**
     * 记录/覆盖某用户某日情绪：uk_user_date 冲突时转 UPDATE 覆盖。
     *
     * @return 影响行数（首次写入 1，覆盖更新 2）
     */
    int upsertToday(@Param("userId") Long userId,
                    @Param("moodDate") LocalDate moodDate,
                    @Param("moodLevel") Integer moodLevel,
                    @Param("color") String color,
                    @Param("word") String word,
                    @Param("note") String note);

    /** 某用户某日期区间内的情绪日记录（按日期升序，月历取数用） */
    List<MoodRecord> selectMonthDays(@Param("userId") Long userId,
                                     @Param("begin") LocalDate begin,
                                     @Param("end") LocalDate end);

    /** 某用户某日期区间内各情绪等级计数（GROUP BY mood_level，按等级升序） */
    List<MoodLevelCountVo> selectLevelCounts(@Param("userId") Long userId,
                                             @Param("begin") LocalDate begin,
                                             @Param("end") LocalDate end);
}
