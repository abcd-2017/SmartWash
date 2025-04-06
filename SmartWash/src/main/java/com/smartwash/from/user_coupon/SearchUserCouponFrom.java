package com.smartwash.from.user_coupon;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;

@Data
public class SearchUserCouponFrom extends BaseSearchFrom {

    private String phoneNumber;

    private Long couponId;
}
