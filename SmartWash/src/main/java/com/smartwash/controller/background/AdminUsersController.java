package com.smartwash.controller.background;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
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
@RequestMapping("/admin/adminUsers")
public class AdminUsersController {
    @Autowired
    private IAdminUsersService adminUsersService;
    @Autowired
    private SecurityUtil securityUtil;

    //获取所有管理员用户
    @GetMapping("/all")
    public Result<Page<AdminUserVo>> getAllAdminUsers(SearchAdminUserFrom adminUserFrom) {
        return Result.ok(adminUsersService.getAllAdminUsers(adminUserFrom));
    }

    //添加管理员用户
    @PostMapping("/add")
    public Result<String> addAdminUser(@RequestBody @Valid AddAdminUserFrom adminUserFrom) {
        if (adminUsersService.getAdminUserByName(adminUserFrom.getUsername()) != null) {
            return Result.failMsg("给管理员用户名已被使用");
        }
        adminUsersService.addAdminUsers(adminUserFrom);
        return Result.ok("添加成功");
    }

    @GetMapping("/getAdminUserInfo")
    public Result<AdminUserVo> getUserInfo() {
        LoginUser currentUser = (LoginUser) securityUtil.getCurrentUser();
        return Result.ok(adminUsersService.getUserById(currentUser.getUserId()));
    }

    //修改管理员用户
    @PostMapping("/update")
    public Result<String> updateSchool(@RequestBody @Valid UpdateAdminUserFrom adminUsersFrom) {
        AdminUsers user = adminUsersService.getById(adminUsersFrom.getAdminId());
        if (!Objects.equals(user.getUsername(), adminUsersFrom.getUsername()) && adminUsersService.getAdminUserByName(adminUsersFrom.getUsername()) != null) {
            return Result.failMsg("给管理员用户名已被使用");
        }
        adminUsersService.updateAdminUsers(adminUsersFrom);
        return Result.ok("修改成功");
    }

    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deleteAdminUsers(@PathVariable("ids") String ids) {
        return Result.ok(adminUsersService.deleteAdminUsers(ids));
    }
}
