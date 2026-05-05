package com.smartwash.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

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
}
