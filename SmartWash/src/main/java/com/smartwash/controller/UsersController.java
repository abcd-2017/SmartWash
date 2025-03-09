package com.smartwash.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.entity.Users;
import com.smartwash.from.users_from.AddUserFrom;
import com.smartwash.from.users_from.SearchUserFrom;
import com.smartwash.from.users_from.UpdateUserFrom;
import com.smartwash.service.IUsersService;
import com.smartwash.vo.users_vo.UserVo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@RestController
@RequestMapping("/users")
public class UsersController {
    @Autowired
    private IUsersService usersService;

    //获取所有用户
    @GetMapping("/all")
    public Result<Page<UserVo>> getAll(SearchUserFrom UsersFrom) {
        return Result.ok(usersService.getAllUsers(UsersFrom));
    }

    //添加用户
    @PostMapping("/add")
    public Result<String> addSchool(@RequestBody @Valid AddUserFrom addUsersFrom) {
        if (StringUtils.hasText(addUsersFrom.getPhoneNumber()) && usersService.getUserByPhone(addUsersFrom.getPhoneNumber()) != null) {
            return Result.fail("该手机号已被使用");
        }
        if (StringUtils.hasText(addUsersFrom.getStudentId()) && usersService.getUserByStudentId(addUsersFrom.getStudentId()) != null) {
            return Result.fail("该学号已注册账号");
        }
        if (StringUtils.hasText(addUsersFrom.getCampusCard()) && usersService.getUserByCampusCard(addUsersFrom.getCampusCard()) != null) {
            return Result.fail("该校园卡已绑定账号");
        }
        usersService.addUsers(addUsersFrom);
        return Result.ok("添加成功");
    }

    //修改用户
    @PostMapping("/update")
    public Result<String> updateSchool(@RequestBody @Valid UpdateUserFrom usersFrom) {
        Users user = usersService.getById(usersFrom.getUserId());
        if ((!Objects.equals(user.getStudentId(), usersFrom.getStudentId())) && (StringUtils.hasText(usersFrom.getStudentId()) && (usersService.getUserByStudentId(usersFrom.getStudentId()) != null))) {
            return Result.fail("该学号已注册账号");
        }
        if (!Objects.equals(user.getCampusCard(), usersFrom.getCampusCard()) && StringUtils.hasText(usersFrom.getCampusCard()) && usersService.getUserByCampusCard(usersFrom.getCampusCard()) != null) {
                return Result.fail("该校园卡已绑定账号");
        }
        usersService.updateUser(usersFrom);
        return Result.ok("修改成功");
    }

    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deleteUsers(@PathVariable("ids") String ids) {
        return Result.ok(usersService.deleteUsers(ids));
    }
}
