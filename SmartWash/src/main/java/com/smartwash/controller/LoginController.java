package com.smartwash.controller;

import com.smartwash.common.DefaultConstant;
import com.smartwash.common.Result;
import com.smartwash.from.admin_users.AdminUserLoginFrom;
import com.smartwash.from.users.UserLoginFrom;
import com.smartwash.from.users.UserRegisterFrom;
import com.smartwash.service.IAdminUsersService;
import com.smartwash.service.IUsersService;
import com.smartwash.utils.JwtUtil;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@RestController
@Slf4j
@RequestMapping("/auth")
public class LoginController {
    @Autowired
    private IAdminUsersService adminUsersService;
    @Autowired
    private IUsersService usersService;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @PostMapping("/adminUsers/login")
    public Result<String> login(@RequestBody @Valid AdminUserLoginFrom userLoginFrom) {
        if (adminUsersService.getAdminUserByName(userLoginFrom.getUsername()) == null)
            return Result.failMsg("该用户名不存在");
        String username = String.format("%s-%s", DefaultConstant.ADMIN_USER_LOGIN_TYPE, userLoginFrom.getUsername());

        //验证用户密码
        Authentication authenticate = null;
        try {
            authenticate = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, userLoginFrom.getPassword()));
        } catch (AuthenticationException e) {
            log.error("管理员登录认证失败", e);
            return Result.failMsg(e.getMessage());
        }
        SecurityContextHolder.getContext().setAuthentication(authenticate);

        String token = jwtUtil.generatorToken(username);
        return Result.ok(token);
    }

    @PostMapping("/user/login")
    public Result<String> login(@RequestBody @Valid UserLoginFrom userLoginFrom) {
        if (usersService.getUserByPhone(userLoginFrom.getPhoneNumber()) == null) {
            return Result.failMsg("用户名或密码错误");
        }
        String username = String.format("%s-%s", DefaultConstant.USER_LOGIN_TYPE, userLoginFrom.getPhoneNumber());

        //验证用户密码
        Authentication authenticate = null;
        try {
            authenticate = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, userLoginFrom.getPassword()));
        } catch (AuthenticationException e) {
            log.error("用户登录认证失败", e);
            return Result.failMsg(e.getMessage());
        }
        SecurityContextHolder.getContext().setAuthentication(authenticate);

        String token = jwtUtil.generatorToken(username);
        return Result.ok(token);
    }

    @PostMapping("/user/register")
    public Result<String> register(@RequestBody @Valid UserRegisterFrom userRegisterFrom) {
        String key = String.format("%s:%s", DefaultConstant.Captcha_Code, userRegisterFrom.getPhoneNumber());
        String code = redisTemplate.opsForValue().get(key);
        if (StringUtils.hasText(code) && userRegisterFrom.getCode().equals(code)) {
            if (usersService.registerUser(userRegisterFrom)) {
                redisTemplate.opsForValue().getAndDelete(key);

                //注册成功后，生成token给用户
                String username = String.format("%s-%s", DefaultConstant.USER_LOGIN_TYPE, userRegisterFrom.getPhoneNumber());
                return Result.ok(jwtUtil.generatorToken(username));
            }
        } else {
            return Result.failMsg("验证码过期，请重新获取");
        }
        return Result.ok(null);
    }

    @GetMapping("/user/captcha/{phoneNumber}")
    public Result<String> getCaptcha(@PathVariable("phoneNumber") String phoneNumber) {
        if (!phoneNumber.matches("^(\\+86)?1[3-9]\\d{9}$")) {
            return Result.failMsg("手机号格式错误");
        }
        // 不区分是否已注册，防止手机号枚举
        String key = String.format("%s:%s", DefaultConstant.Captcha_Code, phoneNumber);

        StringBuilder otp = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            otp.append(random.nextInt(10)); // 生成 0-9 之间的随机数
        }
        redisTemplate.opsForValue().set(key, otp.toString(), DefaultConstant.Captcha_Timeout, TimeUnit.MILLISECONDS);
        return Result.ok(otp.toString());
    }
}
