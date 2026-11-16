package com.smartwash.service;

import com.smartwash.vo.dashboard.DashboardTrendVo;
import com.smartwash.vo.dashboard.DashboardVo;

import java.util.List;

public interface IDashboardService {

    DashboardVo getDashboardStats();

    /**
     * 获取收入与订单趋势（逐日）
     *
     * @param days 统计天数，null/非正数按默认天数，超过上限按上限截断
     * @return 从最早一天到今天的逐日数据，无数据日补零保证折线连续
     */
    List<DashboardTrendVo> getDashboardTrend(Integer days);
}
