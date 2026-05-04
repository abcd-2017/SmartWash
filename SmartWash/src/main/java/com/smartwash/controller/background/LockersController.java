package com.smartwash.controller.background;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.LockerStatusEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.common.Result;
import com.smartwash.from.locker.AddLockerFrom;
import com.smartwash.from.locker.SearchLockersFrom;
import com.smartwash.from.locker.UpdateLockerFrom;
import com.smartwash.service.ILockersService;
import com.smartwash.vo.locker.LockersVo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
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
@Tag(name = "寄存柜管理", description = "寄存柜的增删改查及状态查询")
@Slf4j
@RestController
@RequestMapping("/admin/lockers")
public class LockersController {
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

    @Operation(summary = "新增柜子", description = "新增寄存柜，同一学校下柜子编号不可重复")
    @PostMapping("/add")
    public Result<String> addAdminUser(@RequestBody @Valid AddLockerFrom addLockerFrom) {
        if (lockersService.getLockerById(addLockerFrom.getSchoolId(), addLockerFrom.getLockerNumber()) != null) {
            return Result.failMsg("给存储柜名已被使用");
        }
        lockersService.addLockers(addLockerFrom);
        return Result.ok("添加成功");
    }

    @Operation(summary = "更新柜子", description = "修改寄存柜信息")
    @PostMapping("/update")
    public Result<String> updateSchool(@RequestBody @Valid UpdateLockerFrom lockerFrom) {
        lockersService.updateLockers(lockerFrom);
        return Result.ok("修改成功");
    }

    @Operation(summary = "批量删除柜子", description = "根据ID批量删除寄存柜，多个ID用逗号分隔")
    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deleteLockers(@PathVariable("ids") @Parameter(description = "寄存柜ID列表，多个ID用逗号分隔", required = true, example = "1,2,3") String ids) {
        return Result.ok(lockersService.deleteLockers(ids));
    }
}
