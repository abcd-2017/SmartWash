package com.smartwash.controller.background;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.entity.Users;
import com.smartwash.from.users.AddUserFrom;
import com.smartwash.from.users.SearchUserFrom;
import com.smartwash.from.users.UpdateUserFrom;
import com.smartwash.service.IUsersService;
import com.smartwash.vo.users.UserVo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
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
@Tag(name = "用户管理", description = "后台用户的增删改查")
@Slf4j
@RestController
@RequestMapping("/admin/users")
public class UsersController {
    @Autowired
    private IUsersService usersService;

    @Operation(summary = "分页查询用户列表", description = "根据条件分页查询用户列表")
    @GetMapping("/all")
    public Result<Page<UserVo>> getAll(SearchUserFrom UsersFrom) {
        return Result.ok(usersService.getAllUsers(UsersFrom));
    }

    @Operation(summary = "新增用户", description = "新增用户，手机号、学号、校园卡不可重复")
    @PostMapping("/add")
    public Result<String> addSchool(@RequestBody @Valid AddUserFrom addUsersFrom) {
        if (StringUtils.hasText(addUsersFrom.getPhoneNumber()) && usersService.getUserByPhone(addUsersFrom.getPhoneNumber()) != null) {
            return Result.failMsg("该手机号已被使用");
        }
        if (StringUtils.hasText(addUsersFrom.getStudentId()) && usersService.getUserByStudentId(addUsersFrom.getStudentId()) != null) {
            return Result.failMsg("该学号已注册账号");
        }
        if (StringUtils.hasText(addUsersFrom.getCampusCard()) && usersService.getUserByCampusCard(addUsersFrom.getCampusCard()) != null) {
            return Result.failMsg("该校园卡已绑定账号");
        }
        usersService.addUsers(addUsersFrom);
        return Result.ok("添加成功");
    }

    @Operation(summary = "更新用户", description = "修改用户信息")
    @PostMapping("/update")
    public Result<String> updateSchool(@RequestBody @Valid UpdateUserFrom usersFrom) {
        Users user = usersService.getById(usersFrom.getUserId());
        if ((!Objects.equals(user.getStudentId(), usersFrom.getStudentId())) && (usersService.getUserByStudentId(usersFrom.getStudentId()) != null)) {
            return Result.failMsg("该学号已注册账号");
        }
        if (!Objects.equals(user.getCampusCard(), usersFrom.getCampusCard()) && usersService.getUserByCampusCard(usersFrom.getCampusCard()) != null) {
            return Result.failMsg("该校园卡已绑定账号");
        }
        usersService.updateUser(usersFrom);
        return Result.ok("修改成功");
    }

    @Operation(summary = "批量删除用户", description = "根据ID批量删除用户，多个ID用逗号分隔")
    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deleteUsers(@PathVariable("ids") @Parameter(description = "用户ID列表，多个ID用逗号分隔", required = true, example = "1,2,3") String ids) {
        return Result.ok(usersService.deleteUsers(ids));
    }
}
