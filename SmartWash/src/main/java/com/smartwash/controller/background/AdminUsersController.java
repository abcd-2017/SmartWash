package com.smartwash.controller.background;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.entity.AdminUsers;
import com.smartwash.from.admin_users.AddAdminUserFrom;
import com.smartwash.from.admin_users.SearchAdminUserFrom;
import com.smartwash.from.admin_users.UpdateAdminUserFrom;
import com.smartwash.service.IAdminUsersService;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.SecurityUtil;
import com.smartwash.vo.admin_users.AdminUserVo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
@Tag(name = "管理员管理", description = "管理员用户的增删改查")
@Slf4j
@RestController
@RequestMapping("/admin/adminUsers")
public class AdminUsersController {
    @Autowired
    private IAdminUsersService adminUsersService;
    @Autowired
    private SecurityUtil securityUtil;

    @Operation(summary = "分页查询管理员列表", description = "根据条件分页查询管理员用户列表")
    @GetMapping("/all")
    public Result<Page<AdminUserVo>> getAllAdminUsers(SearchAdminUserFrom adminUserFrom) {
        return Result.ok(adminUsersService.getAllAdminUsers(adminUserFrom));
    }

    @Operation(summary = "新增管理员", description = "新增管理员用户，用户名不可重复")
    @PostMapping("/add")
    public Result<String> addAdminUser(@RequestBody @Valid AddAdminUserFrom adminUserFrom) {
        if (adminUsersService.getAdminUserByName(adminUserFrom.getUsername()) != null) {
            return Result.failMsg("给管理员用户名已被使用");
        }
        adminUsersService.addAdminUsers(adminUserFrom);
        return Result.ok("添加成功");
    }

    @Operation(summary = "获取当前管理员信息", description = "获取当前登录管理员的详细信息")
    @GetMapping("/getAdminUserInfo")
    public Result<AdminUserVo> getUserInfo() {
        LoginUser currentUser = (LoginUser) securityUtil.getCurrentUser();
        return Result.ok(adminUsersService.getUserById(currentUser.getUserId()));
    }

    @Operation(summary = "更新管理员", description = "修改管理员用户信息")
    @PostMapping("/update")
    public Result<String> updateSchool(@RequestBody @Valid UpdateAdminUserFrom adminUsersFrom) {
        AdminUsers user = adminUsersService.getById(adminUsersFrom.getAdminId());
        if (!Objects.equals(user.getUsername(), adminUsersFrom.getUsername()) && adminUsersService.getAdminUserByName(adminUsersFrom.getUsername()) != null) {
            return Result.failMsg("给管理员用户名已被使用");
        }
        adminUsersService.updateAdminUsers(adminUsersFrom);
        return Result.ok("修改成功");
    }

    @Operation(summary = "批量删除管理员", description = "根据ID批量删除管理员用户，多个ID用逗号分隔")
    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deleteAdminUsers(@PathVariable("ids") @Parameter(description = "管理员ID列表，多个ID用逗号分隔", required = true, example = "1,2,3") String ids) {
        return Result.ok(adminUsersService.deleteAdminUsers(ids));
    }
}
