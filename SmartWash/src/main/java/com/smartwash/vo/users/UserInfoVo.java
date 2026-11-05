package com.smartwash.vo.users;

import com.smartwash.vo.schools.SchoolsVo;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UserInfoVo {
    private String phoneNumber;

    private String studentId;

    private String campusCard;

    private BigDecimal balance;

    private String avatar;

    private SchoolsVo schoolVo;

    /**
     * 订单总数：该用户全部状态订单数之和（复用 OrdersMapper.countGroupByStatus 的
     * GROUP BY status 聚合后求和，与订单页「全部」tab 的 allTotal 同口径）。
     * 消费端：Android / 鸿蒙用户中心统计展示（P-201 走查，两端声明均为可空数值）。
     */
    private Integer orderCount;

    /**
     * 可用优惠券张数：未使用（is_used = 0）且未过期（expired_at > now()），
     * 口径与优惠券页「可用」tab（searchUserCouponByStatus 可用分支）一致。
     * 消费端：Android / 鸿蒙用户中心统计展示（P-201 走查，两端声明均为可空数值）。
     */
    private Integer couponCount;
}
