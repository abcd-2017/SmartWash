package com.smartwash.workbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.workbench.entity.WorkbenchEntry;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作台入口配置 Mapper（MyBatis-Plus 内置方法即可覆盖，无自定义 SQL）。
 */
@Mapper
public interface WorkbenchEntryMapper extends BaseMapper<WorkbenchEntry> {
}
