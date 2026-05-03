package com.smartwash.controller.web;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.LockerStatusEnum;
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
@Slf4j
@RestController
@RequestMapping("/web/lockers")
public class WebLockersController {
    @Autowired
    private ILockersService lockersService;

    //获取存储柜状态
    @GetMapping("/status")
    public Result<Map<String, String>> getLockersStatus() {
        LockerStatusEnum[] values = LockerStatusEnum.values();
        Map<String, String> map = new HashMap<>();
        for (LockerStatusEnum status : values) {
            map.put(status.getValue(), status.getDescription());
        }
        return Result.ok(map);
    }

    //获取所有存储柜
    @GetMapping("/all")
    public Result<Page<LockersVo>> getAllLockers(SearchLockersFrom lockersFrom) {
        return Result.ok(lockersService.getAllLockers(lockersFrom));
    }
}
