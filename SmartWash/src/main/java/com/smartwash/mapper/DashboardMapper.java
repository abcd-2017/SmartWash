package com.smartwash.mapper;

import com.smartwash.vo.dashboard.DashboardTrendVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 工作台统计 Mapper
 */
@Mapper
public interface DashboardMapper {

    /** 学校总数 */
    Long countSchools();

    /** 用户总数 */
    Long countUsers();

    /** 今日订单数 */
    Long countTodayOrders(@Param("todayStart") String todayStart);

    /** 今日收入 */
    BigDecimal sumTodayIncome(@Param("todayStart") String todayStart);

    /** 逐日支付成功收入（趋势图）：[startDate, endDate) 左闭右开区间，按日聚合 */
    List<DashboardTrendVo> selectIncomeTrendByDay(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /** 逐日订单创建数（趋势图）：[startDate, endDate) 左闭右开区间，按日聚合 */
    List<DashboardTrendVo> selectOrderTrendByDay(@Param("startDate") String startDate, @Param("endDate") String endDate);
}
