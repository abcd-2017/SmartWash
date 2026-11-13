package com.smartwash.toolbox.assettag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.toolbox.assettag.entity.ToolboxAssetTag;

/**
 * 物品标签明细 Mapper（一物一码 M2，独立子包经 @MapperScan 精确扫描，对齐既有 toolbox mapper 约定）。
 */
public interface ToolboxAssetTagMapper extends BaseMapper<ToolboxAssetTag> {
}
