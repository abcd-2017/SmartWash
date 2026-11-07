package com.smartwash.readlater.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.readlater.entity.ReadLater;
import org.apache.ibatis.annotations.Mapper;

/**
 * 稍后读 Mapper（内置方法足够，无自定义 SQL）。
 */
@Mapper
public interface ReadLaterMapper extends BaseMapper<ReadLater> {
}
