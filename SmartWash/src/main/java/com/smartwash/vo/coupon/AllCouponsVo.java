package com.smartwash.vo.coupon;

import com.smartwash.vo.user_coupon.UserCouponVo;
import lombok.Data;

import java.util.List;

@Data
public class AllCouponsVo {
    private List<CouponVo> available;
    private List<UserCouponVo> claimed;
    private List<UserCouponVo> historical;
}