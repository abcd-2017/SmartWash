package com.smartwash.controller.background;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.LockerStatusEnum;
import com.smartwash.common.Result;
import com.smartwash.from.locker.AddLockerFrom;
import com.smartwash.from.locker.SearchLockersFrom;
import com.smartwash.from.locker.UpdateLockerFrom;
import com.smartwash.service.ILockersService;
import com.smartwash.vo.locker.LockersVo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
@RestController
@RequestMapping("/admin/lockers")
public class LockersController {
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

    //添加存储柜
    @PostMapping("/add")
    public Result<String> addAdminUser(@RequestBody @Valid AddLockerFrom addLockerFrom) {
        if (lockersService.getLockerById(addLockerFrom.getSchoolId(), addLockerFrom.getLockerNumber()) != null) {
            return Result.failMsg("给存储柜名已被使用");
        }
        lockersService.addLockers(addLockerFrom);
        return Result.ok("添加成功");
    }

    //修改存储柜
    @PostMapping("/update")
    public Result<String> updateSchool(@RequestBody @Valid UpdateLockerFrom lockerFrom) {
        lockersService.updateLockers(lockerFrom);
        return Result.ok("修改成功");
    }

    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deleteLockers(@PathVariable("ids") String ids) {
        return Result.ok(lockersService.deleteLockers(ids));
    }
}
