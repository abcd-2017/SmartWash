package com.smartwash.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.entity.LoginLog;

/**
 * 登录日志 Mapper。
 *
 * <p>分页查询条件简单（账号/平台/结果/时间范围），直接用 MyBatis-Plus 内置方法，无需自定义 XML。
 */
public interface LoginLogMapper extends BaseMapper<LoginLog> {
}
