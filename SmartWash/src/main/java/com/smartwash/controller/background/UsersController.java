package com.smartwash.controller.background;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.common.audit.Audited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.entity.Users;
import com.smartwash.from.users.AddUserFrom;
import com.smartwash.from.users.SearchUserFrom;
import com.smartwash.from.users.UpdateUserFrom;
import com.smartwash.service.IUsersService;
import com.smartwash.vo.users.UserVo;
import cn.hutool.core.util.DesensitizedUtil;
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
    @Audited(action = "USER_ADD", targetType = "user")
    @PostMapping("/add")
    public Result<String> addUser(@RequestBody @Valid AddUserFrom addUsersFrom) {
        if (StringUtils.hasText(addUsersFrom.getPhoneNumber()) && usersService.getUserByPhone(addUsersFrom.getPhoneNumber()) != null) {
            log.warn("新增用户被拒绝：手机号已被使用, phone: {}", DesensitizedUtil.mobilePhone(addUsersFrom.getPhoneNumber()));
            return Result.failMsg("该手机号已被使用");
        }
        if (StringUtils.hasText(addUsersFrom.getStudentId()) && usersService.getUserByStudentId(addUsersFrom.getStudentId()) != null) {
            log.warn("新增用户被拒绝：学号已注册, studentId: {}", addUsersFrom.getStudentId());
            return Result.failMsg("该学号已注册账号");
        }
        if (StringUtils.hasText(addUsersFrom.getCampusCard()) && usersService.getUserByCampusCard(addUsersFrom.getCampusCard()) != null) {
            log.warn("新增用户被拒绝：校园卡已绑定, campusCard: {}", addUsersFrom.getCampusCard());
            return Result.failMsg("该校园卡已绑定账号");
        }
        usersService.addUsers(addUsersFrom);
        log.info("新增用户成功, phone: {}", DesensitizedUtil.mobilePhone(addUsersFrom.getPhoneNumber()));
        return Result.ok("添加成功");
    }

    @Operation(summary = "更新用户", description = "修改用户信息")
    @Audited(action = "USER_UPDATE", targetType = "user", targetIdSpEL = "#usersFrom.userId")
    @PostMapping("/update")
    public Result<String> updateUser(@RequestBody @Valid UpdateUserFrom usersFrom) {
        Users user = usersService.getById(usersFrom.getUserId());
        if (user == null) {
            log.warn("更新用户被拒绝：用户不存在, userId: {}", usersFrom.getUserId());
            return Result.failMsg("用户不存在");
        }
        // 手机号变更前查重：重复时返回友好提示，而非依赖唯一索引报 500（评审报告后端 #29）
        if (StringUtils.hasText(usersFrom.getPhoneNumber())
                && !Objects.equals(user.getPhoneNumber(), usersFrom.getPhoneNumber())
                && usersService.getUserByPhone(usersFrom.getPhoneNumber()) != null) {
            log.warn("更新用户被拒绝：手机号已被其他账号使用, userId: {}, phone: {}", usersFrom.getUserId(), DesensitizedUtil.mobilePhone(usersFrom.getPhoneNumber()));
            return Result.failMsg("该手机号已被使用");
        }
        if ((!Objects.equals(user.getStudentId(), usersFrom.getStudentId())) && (usersService.getUserByStudentId(usersFrom.getStudentId()) != null)) {
            log.warn("更新用户被拒绝：学号已注册, userId: {}, studentId: {}", usersFrom.getUserId(), usersFrom.getStudentId());
            return Result.failMsg("该学号已注册账号");
        }
        if (!Objects.equals(user.getCampusCard(), usersFrom.getCampusCard()) && usersService.getUserByCampusCard(usersFrom.getCampusCard()) != null) {
            log.warn("更新用户被拒绝：校园卡已绑定其他账号, userId: {}", usersFrom.getUserId());
            return Result.failMsg("该校园卡已绑定账号");
        }
        usersService.updateUser(usersFrom);
        log.info("更新用户成功, userId: {}", usersFrom.getUserId());
        return Result.ok("修改成功");
    }

    @Operation(summary = "批量删除用户", description = "根据ID批量删除用户，多个ID用逗号分隔")
    @Audited(action = "USER_DELETE", targetType = "user", targetIdSpEL = "#ids")
    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deleteUsers(@PathVariable("ids") @Parameter(description = "用户ID列表，多个ID用逗号分隔", required = true, example = "1,2,3") String ids) {
        log.info("批量删除用户, ids: {}", ids);
        return Result.ok(usersService.deleteUsers(ids));
    }
}
