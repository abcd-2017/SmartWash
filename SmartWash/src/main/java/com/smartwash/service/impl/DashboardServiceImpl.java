package com.smartwash.service.impl;

import com.smartwash.mapper.DashboardMapper;
import com.smartwash.service.IDashboardService;
import com.smartwash.vo.dashboard.DashboardTrendVo;
import com.smartwash.vo.dashboard.DashboardVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements IDashboardService {

    /** 趋势默认统计天数 */
    static final int DEFAULT_TREND_DAYS = 30;
    /** 趋势统计天数上限 */
    static final int MAX_TREND_DAYS = 90;

    private final DashboardMapper dashboardMapper;

    @Override
    @Cacheable(value = "dashboard", key = "'stats'")
    public DashboardVo getDashboardStats() {
        String todayStart = LocalDate.now().atStartOfDay().toString();

        DashboardVo vo = new DashboardVo();
        vo.setSchoolCount(dashboardMapper.countSchools());
        vo.setUserCount(dashboardMapper.countUsers());
        vo.setTodayOrderCount(dashboardMapper.countTodayOrders(todayStart));
        BigDecimal income = dashboardMapper.sumTodayIncome(todayStart);
        vo.setTodayIncome(income != null ? income : BigDecimal.ZERO);
        return vo;
    }

    @Override
    public List<DashboardTrendVo> getDashboardTrend(Integer days) {
        int trendDays = normalizeTrendDays(days);
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(trendDays - 1L);
        // 左闭右开区间：起始日零点 ~ 次日零点，与 sumTodayIncome 的字符串时间戳比较方式保持一致
        String start = startDate.atStartOfDay().toString();
        String end = today.plusDays(1).atStartOfDay().toString();

        // 两条 GROUP BY 查询结果按日合并
        Map<String, DashboardTrendVo> trendByDate = new HashMap<>();
        for (DashboardTrendVo row : dashboardMapper.selectIncomeTrendByDay(start, end)) {
            trendByDate.computeIfAbsent(row.getDate(), k -> new DashboardTrendVo()).setIncome(row.getIncome());
        }
        for (DashboardTrendVo row : dashboardMapper.selectOrderTrendByDay(start, end)) {
            DashboardTrendVo vo = trendByDate.computeIfAbsent(row.getDate(), k -> new DashboardTrendVo());
            vo.setDate(row.getDate());
            vo.setOrderCount(row.getOrderCount());
        }

        // 空日补零：逐日构建完整序列，保证前端折线连续
        List<DashboardTrendVo> trend = new ArrayList<>(trendDays);
        for (LocalDate date = startDate; !date.isAfter(today); date = date.plusDays(1)) {
            DashboardTrendVo vo = trendByDate.get(date.toString());
            if (vo == null) {
                vo = new DashboardTrendVo();
                vo.setDate(date.toString());
                vo.setIncome(BigDecimal.ZERO);
                vo.setOrderCount(0);
            }
            if (vo.getIncome() == null) {
                vo.setIncome(BigDecimal.ZERO);
            }
            if (vo.getOrderCount() == null) {
                vo.setOrderCount(0);
            }
            trend.add(vo);
        }
        return trend;
    }

    /** 天数归一化：null/非正数取默认值，超过上限按上限截断 */
    private int normalizeTrendDays(Integer days) {
        if (days == null || days <= 0) {
            return DEFAULT_TREND_DAYS;
        }
        return Math.min(days, MAX_TREND_DAYS);
    }
}
