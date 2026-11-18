package com.smartwash.controller.background;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.common.audit.Audited;
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

@Tag(name = "管理员管理", description = "管理员用户的增删改查")
@Slf4j
@RestController
@RequestMapping("/admin/adminUsers")
public class AdminUsersController {
    /** 超管角色名（roles 表 role_name）：管理员账号的增删改仅超管可操作，防止 role2/role3 越权操作超管账号 */
    private static final String ROLE_SUPER_ADMIN = "root";

    @Autowired
    private IAdminUsersService adminUsersService;
    @Autowired
    private SecurityUtil securityUtil;

    /**
     * 管理员账号管理门禁：仅超管（roles.role_name=root）可操作。
     * 修复越权漏洞：此前 schools_admin 可新增/删除乃至删除超管账号——角色信息不在 JWT authorities
     * （authorities 仅粗粒度 ROLE_ADMIN），必须回查 roles 表业务角色。
     */
    private boolean isSuperAdmin() {
        LoginUser currentUser = (LoginUser) securityUtil.getCurrentUser();
        if (currentUser == null || currentUser.getUsername() == null) {
            return false;
        }
        String roleName = adminUsersService.getAdminRoleName(currentUser.getUsername());
        if (!ROLE_SUPER_ADMIN.equals(roleName)) {
            log.warn("管理员管理被拒绝：非超管角色, username: {}, role: {}", currentUser.getUsername(), roleName);
            return false;
        }
        return true;
    }

    @Operation(summary = "分页查询管理员列表", description = "根据条件分页查询管理员用户列表")
    @GetMapping("/all")
    public Result<Page<AdminUserVo>> getAllAdminUsers(SearchAdminUserFrom adminUserFrom) {
        return Result.ok(adminUsersService.getAllAdminUsers(adminUserFrom));
    }

    @Operation(summary = "新增管理员", description = "新增管理员用户，用户名不可重复")
    @Audited(action = "ADMIN_USER_ADD", targetType = "admin_user")
    @PostMapping("/add")
    public Result<String> addAdminUser(@RequestBody @Valid AddAdminUserFrom adminUserFrom) {
        if (!isSuperAdmin()) {
            return Result.failMsg("仅超级管理员可管理管理员账号");
        }
        if (adminUsersService.getAdminUserByName(adminUserFrom.getUsername()) != null) {
            log.warn("新增管理员被拒绝：用户名已被使用, username: {}", adminUserFrom.getUsername());
            return Result.failMsg("管理员用户名已被使用");
        }
        adminUsersService.addAdminUsers(adminUserFrom);
        log.info("新增管理员成功, username: {}", adminUserFrom.getUsername());
        return Result.ok("添加成功");
    }

    @Operation(summary = "获取当前管理员信息", description = "获取当前登录管理员的详细信息")
    @GetMapping("/getAdminUserInfo")
    public Result<AdminUserVo> getUserInfo() {
        LoginUser currentUser = (LoginUser) securityUtil.getCurrentUser();
        return Result.ok(adminUsersService.getUserById(currentUser.getUserId()));
    }

    @Operation(summary = "更新管理员", description = "修改管理员用户信息")
    @Audited(action = "ADMIN_USER_UPDATE", targetType = "admin_user", targetIdSpEL = "#adminUsersFrom.adminId")
    @PostMapping("/update")
    public Result<String> updateAdminUser(@RequestBody @Valid UpdateAdminUserFrom adminUsersFrom) {
        if (!isSuperAdmin()) {
            return Result.failMsg("仅超级管理员可管理管理员账号");
        }
        AdminUsers user = adminUsersService.getById(adminUsersFrom.getAdminId());
        if (user == null) {
            log.warn("更新管理员被拒绝：管理员不存在, adminId: {}", adminUsersFrom.getAdminId());
            return Result.failMsg("管理员不存在");
        }
        if (!Objects.equals(user.getUsername(), adminUsersFrom.getUsername()) && adminUsersService.getAdminUserByName(adminUsersFrom.getUsername()) != null) {
            log.warn("更新管理员被拒绝：用户名已被使用, adminId: {}, username: {}", adminUsersFrom.getAdminId(), adminUsersFrom.getUsername());
            return Result.failMsg("管理员用户名已被使用");
        }
        adminUsersService.updateAdminUsers(adminUsersFrom);
        log.info("更新管理员成功, adminId: {}", adminUsersFrom.getAdminId());
        return Result.ok("修改成功");
    }

    @Operation(summary = "批量删除管理员", description = "根据ID批量删除管理员用户，多个ID用逗号分隔。不能删除当前登录账号，且至少保留一个管理员")
    @Audited(action = "ADMIN_USER_DELETE", targetType = "admin_user", targetIdSpEL = "#ids")
    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deleteAdminUsers(@PathVariable("ids") @Parameter(description = "管理员ID列表，多个ID用逗号分隔", required = true, example = "1,2,3") String ids) {
        if (!isSuperAdmin()) {
            return Result.failMsg("仅超级管理员可管理管理员账号");
        }
        log.info("批量删除管理员, ids: {}", ids);
        // 当前登录管理员从 SecurityContext 取出传入 Service（与 getAdminUserInfo 同模式），用于防自删校验
        LoginUser currentUser = (LoginUser) securityUtil.getCurrentUser();
        Long currentAdminId = currentUser != null ? currentUser.getUserId() : null;
        return Result.ok(adminUsersService.deleteAdminUsers(ids, currentAdminId));
    }
}
