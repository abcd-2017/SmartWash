package com.smartwash.service;

import com.smartwash.vo.dashboard.DashboardVo;

/**
 * 工作台统计服务
 */
public interface IDashboardService {

    /** 获取工作台统计数据 */
    DashboardVo getDashboardStats();
}
