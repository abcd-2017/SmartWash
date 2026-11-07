package com.smartwash.divination.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.divination.entity.DivRecord;
import com.smartwash.divination.vo.DivMonthCountVo;
import com.smartwash.divination.vo.DivNameCountVo;
import com.smartwash.divination.vo.DivRecordSearchVo;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 卦例主表 Mapper。
 */
public interface DivRecordMapper extends BaseMapper<DivRecord> {

    /**
     * 解读历史检索：keyword 匹配卦例问题或该卦最新一条解读正文（占卜闭环新增）。
     */
    Page<DivRecordSearchVo> searchByKeyword(Page<DivRecordSearchVo> page,
                                            @Param("userId") Long userId,
                                            @Param("keyword") String keyword);

    /**
     * 盘感统计：按术数方法计数（单用户全量）。
     */
    List<DivNameCountVo> selectMethodCounts(@Param("userId") Long userId);

    /**
     * 盘感统计：按问题领域计数（单用户全量）。
     */
    List<DivNameCountVo> selectCategoryCounts(@Param("userId") Long userId);

    /**
     * 盘感统计：近 12 个月卦例趋势（start = 当前月往前 11 个月的首日，缺失月份服务层补 0）。
     */
    List<DivMonthCountVo> selectMonthTrend(@Param("userId") Long userId,
                                           @Param("start") LocalDateTime start);
}
