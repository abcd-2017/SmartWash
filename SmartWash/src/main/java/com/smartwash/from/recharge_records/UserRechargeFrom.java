package com.smartwash.from.recharge_records;

import lombok.Data;

@Data
public class UserRechargeFrom {
    private Float amount;
    //1--微信支付，2--支付宝支付
    private String rechargeType;
}
