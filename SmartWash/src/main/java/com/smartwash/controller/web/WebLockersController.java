package com.smartwash.controller.web;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.LockerStatusEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.common.Result;
import com.smartwash.from.locker.SearchLockersFrom;
import com.smartwash.service.ILockersService;
import com.smartwash.vo.locker.LockersVo;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@Tag(name = "用户端-寄存柜", description = "用户端寄存柜查询接口")
@Slf4j
@RestController
@RequestMapping("/web/lockers")
public class WebLockersController {
    @Autowired
    private ILockersService lockersService;

    @Operation(summary = "获取柜子状态枚举", description = "获取所有寄存柜状态的枚举值及描述")
    @GetMapping("/status")
    public Result<Map<String, String>> getLockersStatus() {
        LockerStatusEnum[] values = LockerStatusEnum.values();
        Map<String, String> map = new HashMap<>();
        for (LockerStatusEnum status : values) {
            map.put(status.getValue(), status.getDescription());
        }
        return Result.ok(map);
    }

    @Operation(summary = "分页查询柜子列表", description = "根据条件分页查询寄存柜列表")
    @GetMapping("/all")
    public Result<Page<LockersVo>> getAllLockers(SearchLockersFrom lockersFrom) {
        return Result.ok(lockersService.getAllLockers(lockersFrom));
    }
}
