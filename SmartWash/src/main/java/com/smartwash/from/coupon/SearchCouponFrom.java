package com.smartwash.from.coupon;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;

@Data
public class SearchCouponFrom extends BaseSearchFrom {
    private Long couponId;
    private String title;
    private String status;
}
