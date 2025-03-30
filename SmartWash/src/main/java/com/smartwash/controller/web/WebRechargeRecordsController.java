package com.smartwash.controller.web;


import com.smartwash.common.Result;
import com.smartwash.from.recharge_records.UserRechargeFrom;
import com.smartwash.service.IRechargeRecordsService;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@RestController
@RequestMapping("/web")
public class WebRechargeRecordsController {
    @Autowired
    private IRechargeRecordsService rechargeRecordsService;

    @PostMapping("/auth/recharge/userRecharge")
    public Result<String> rechargeUserRecharge(@RequestBody UserRechargeFrom vo) {
        LoginUser loginUser = UserContextHolder.getUser();
        if (rechargeRecordsService.userRecharge(vo, loginUser.getUserId())) {
            return Result.ok("充值成功");
        } else {
            return Result.failMsg("充值失败");
        }
    }
}
