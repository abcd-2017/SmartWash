package com.smartwash.service.impl;

import com.smartwash.mapper.DashboardMapper;
import com.smartwash.service.IDashboardService;
import com.smartwash.vo.dashboard.DashboardVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements IDashboardService {

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
}
