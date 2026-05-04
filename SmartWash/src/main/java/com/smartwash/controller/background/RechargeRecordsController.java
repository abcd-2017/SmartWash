package com.smartwash.controller.background;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.from.recharge_records.SearchRechargeRecordsFrom;
import com.smartwash.service.IRechargeRecordsService;
import com.smartwash.vo.recharge_records.RechargeRecordsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@Tag(name = "充值记录管理", description = "后台充值记录查询")
@Slf4j
@RestController
@RequestMapping("/admin/rechargeRecords")
public class RechargeRecordsController {
    @Autowired
    private IRechargeRecordsService rechargeRecordsService;

    @Operation(summary = "分页查询充值记录列表", description = "根据条件分页查询用户充值记录列表")
    @GetMapping("/all")
    public Result<Page<RechargeRecordsVo>> getAll(SearchRechargeRecordsFrom rechargeRecordsFrom) {
        String phoneNumber = rechargeRecordsFrom.getPhoneNumber();
        if (StringUtils.hasText(phoneNumber)) {
            if (phoneNumber.length() > 11) return Result.failMsg("手机号长度错误");
            if (!phoneNumber.matches("^(\\+86)?1[3-9]\\d{9}$")) return Result.failMsg("手机号格式错误");
        }
        return Result.ok(rechargeRecordsService.getAllRecords(rechargeRecordsFrom));
    }
}
