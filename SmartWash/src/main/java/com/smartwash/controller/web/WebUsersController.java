package com.smartwash.controller.web;


import com.smartwash.common.DefaultConstant;
import com.smartwash.common.Result;
import com.smartwash.entity.Users;
import com.smartwash.from.users.UpdateUserInfo;
import com.smartwash.service.IUsersService;
import com.smartwash.utils.JwtUtil;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import com.smartwash.vo.users.UserInfoVo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
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
@RequestMapping("/web")
public class WebUsersController {
    @Autowired
    private IUsersService usersService;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private AuthenticationManager authenticationManager;

    @PostMapping("/auth/user/updateUserInfo")
    public Result<String> updateUserInfo(@RequestBody @Valid UpdateUserInfo updateUserInfo) {
        LoginUser user = UserContextHolder.getUser();
        if (!Objects.equals(user.getUserType(), DefaultConstant.USER_LOGIN_TYPE) && user.getUserId() == null) {
            return Result.failMsg("系统异常");
        }
        usersService.updateUserInfo(updateUserInfo, user.getUserId());
        return Result.ok("修改成功");
    }

    @PostMapping("/auth/user/getUserSchool")
    public Result<Long> getUserSchoolId() {
        LoginUser user = UserContextHolder.getUser();
        Users users = usersService.getById(user.getUserId());
        return Result.ok(users.getSchoolId());
    }

    @GetMapping("/auth/user/getUserByStudentId")
    public Result<Boolean> getUserByStudentId(@RequestParam("studentId") String studentId) {
        Users users = usersService.getUserByStudentId(studentId);
        return Result.ok(users == null);
    }

    @GetMapping("/auth/user/getUserInfo")
    public Result<UserInfoVo> getUserInfo() {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(usersService.getUserInfo(user.getUserId()));
    }
}
