package com.smartwash.controller.web;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.from.BaseSearchFrom;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.from.recharge_records.UserRechargeFrom;
import com.smartwash.service.IRechargeRecordsService;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import com.smartwash.vo.recharge_records.RechargeRecordsVo;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
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
@Tag(name = "用户端-充值", description = "用户端充值相关接口")
@Slf4j
@RestController
@RequestMapping("/web")
public class WebRechargeRecordsController {
    @Autowired
    private IRechargeRecordsService rechargeRecordsService;

    @Operation(summary = "用户充值", description = "用户向账户余额充值")
    @PostMapping("/auth/recharge/userRecharge")
    public Result<String> rechargeUserRecharge(@RequestBody UserRechargeFrom vo) {
        LoginUser loginUser = UserContextHolder.getUser();
        if (rechargeRecordsService.userRecharge(vo, loginUser.getUserId())) {
            return Result.ok("充值成功");
        } else {
            return Result.failMsg("充值失败");
        }
    }

    @Operation(summary = "获取当前用户充值记录", description = "分页查询当前登录用户的充值记录")
    @GetMapping("/auth/recharge/list")
    public Result<Page<RechargeRecordsVo>> getUserRechargeRecords(BaseSearchFrom searchFrom) {
        LoginUser user = UserContextHolder.getUser();
        Page<RechargeRecordsVo> page = rechargeRecordsService.getUserRechargeRecords(user.getUserId(), searchFrom);
        return Result.ok(page);
    }
}
