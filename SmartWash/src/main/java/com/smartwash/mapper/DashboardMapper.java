package com.smartwash.mapper;

import com.smartwash.vo.dashboard.DashboardTrendVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface DashboardMapper {

    Long countSchools();

    Long countUsers();

    Long countTodayOrders(@Param("todayStart") String todayStart);

    BigDecimal sumTodayIncome(@Param("todayStart") String todayStart);

    /** 逐日支付成功收入（趋势图）：[startDate, endDate) 左闭右开区间，按日聚合 */
    List<DashboardTrendVo> selectIncomeTrendByDay(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /** 逐日订单创建数（趋势图）：[startDate, endDate) 左闭右开区间，按日聚合 */
    List<DashboardTrendVo> selectOrderTrendByDay(@Param("startDate") String startDate, @Param("endDate") String endDate);
}
