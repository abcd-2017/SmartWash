package com.smartwash.controller.web;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.DefaultConstant;
import com.smartwash.common.Result;
import com.smartwash.entity.Users;
import com.smartwash.from.users.AddUserFrom;
import com.smartwash.from.users.SearchUserFrom;
import com.smartwash.from.users.UpdateUserFrom;
import com.smartwash.from.users.UserLoginFrom;
import com.smartwash.service.IUsersService;
import com.smartwash.utils.JwtUtil;
import com.smartwash.vo.users.UserVo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
@RequestMapping("/web/users")
public class WebUsersController {
    @Autowired
    private IUsersService usersService;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private AuthenticationManager authenticationManager;

    //获取所有用户
    @GetMapping("/all")
    public Result<Page<UserVo>> getAll(SearchUserFrom UsersFrom) {
        return Result.ok(usersService.getAllUsers(UsersFrom));
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody @Valid UserLoginFrom userLoginFrom) {
        if (usersService.getUserByPhone(userLoginFrom.getPhoneNumber()) == null) {
            return Result.failMsg("改手机号暂未注册！");
        }
        String username = String.format("%s-%s", DefaultConstant.USER_LOGIN_TYPE, userLoginFrom.getPhoneNumber());

        //验证用户密码
        Authentication authenticate = null;
        try {
            authenticate = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, userLoginFrom.getPassword()));
        } catch (AuthenticationException e) {
            e.printStackTrace();
            return Result.failMsg(e.getMessage());
        }
        SecurityContextHolder.getContext().setAuthentication(authenticate);

        String token = jwtUtil.generatorToken(username);
        return Result.ok(token);
    }

    @PostMapping("/register")
    public Result<String> register(@RequestParam("username") String username, @RequestParam("password") String password) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String encode = encoder.encode(password);
        return Result.ok(encode);
    }

    //添加用户
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

    //修改用户
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

    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deleteUsers(@PathVariable("ids") String ids) {
        return Result.ok(usersService.deleteUsers(ids));
    }
}
