package com.smartwash.notice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.notice.entity.Notice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站内信主表 Mapper（MyBatis-Plus 内置方法即可覆盖，无自定义 SQL）。
 */
@Mapper
public interface NoticeMapper extends BaseMapper<Notice> {
}
