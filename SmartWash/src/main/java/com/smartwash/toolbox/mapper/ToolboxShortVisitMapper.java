package com.smartwash.toolbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import com.smartwash.toolbox.entity.ToolboxShortVisit;

/**
 * 短码访问明细 Mapper。
 */
public interface ToolboxShortVisitMapper extends BaseMapper<ToolboxShortVisit> {

    /** 全局清扫无主明细：以现存短码表为基准，删除所有悬挂访问明细（含历史悬挂） */
    @Delete("DELETE FROM toolbox_short_visit WHERE code NOT IN (SELECT code FROM toolbox_short_code)")
    int sweepOrphanVisits();
}
