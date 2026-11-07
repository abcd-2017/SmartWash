package com.smartwash.divination.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.divination.entity.DivFeedbackNudge;
import com.smartwash.divination.vo.DivNudgeVo;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 应验回访提醒 Mapper。
 */
public interface DivFeedbackNudgeMapper extends BaseMapper<DivFeedbackNudge> {

    /**
     * 分页查询当前用户的待回访提醒（关联卦例取展示字段），按 nudge_date 倒序。
     */
    Page<DivNudgeVo> selectNudgePage(Page<DivNudgeVo> page, @Param("userId") Long userId);

    /**
     * 扫描回访候选：解读完成时间早于 threshold、该解读无任何反馈、且无既有提醒。
     * nudge_date 由 SQL 直接计算为「解读完成日 + 3 天」。
     */
    List<DivFeedbackNudge> selectNudgeCandidates(@Param("threshold") LocalDateTime threshold);
}
