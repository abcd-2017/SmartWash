package com.smartwash.controller;

import com.smartwash.common.DefaultConstant;
import com.smartwash.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "认证管理", description = "用户登录、注册、验证码接口")
@RestController
@Slf4j
@RequestMapping("/auth")
public class LoginController {

    private static final String PHONE_REGEX = "^(\\+86)?1[3-9]\\d{9}$";
    private static final int CAPTCHA_LENGTH = 6;

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

    @Operation(summary = "管理员登录", description = "管理员通过用户名和密码登录，返回JWT令牌")
    @PostMapping("/adminUsers/login")
    public Result<String> login(@RequestBody @Valid AdminUserLoginFrom userLoginFrom) {
        if (adminUsersService.getAdminUserByName(userLoginFrom.getUsername()) == null) {
            log.warn("管理员登录失败：用户名不存在, username: {}", userLoginFrom.getUsername());
            return Result.failMsg("该用户名不存在");
        }
        String username = String.format("%s-%s", DefaultConstant.ADMIN_USER_LOGIN_TYPE, userLoginFrom.getUsername());

        //验证用户密码
        Authentication authenticate = null;
        try {
            authenticate = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, userLoginFrom.getPassword()));
        } catch (AuthenticationException e) {
            log.error("管理员登录认证失败", e);
            return Result.failMsg("用户名或密码错误");
        } catch (Exception e) {
            log.error("管理员登录异常", e);
            return Result.failMsg("系统异常，请稍后再试");
        }
        SecurityContextHolder.getContext().setAuthentication(authenticate);

        String token = jwtUtil.generatorToken(username);
        log.info("管理员登录成功, username: {}", userLoginFrom.getUsername());
        return Result.ok(token);
    }

    @Operation(summary = "用户登录", description = "用户通过手机号和密码登录，返回JWT令牌")
    @PostMapping("/user/login")
    public Result<String> login(@RequestBody @Valid UserLoginFrom userLoginFrom) {
        if (usersService.getUserByPhone(userLoginFrom.getPhoneNumber()) == null) {
            log.warn("用户登录失败：手机号未注册, phone: {}", userLoginFrom.getPhoneNumber().replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
            return Result.failMsg("用户名或密码错误");
        }
        String username = String.format("%s-%s", DefaultConstant.USER_LOGIN_TYPE, userLoginFrom.getPhoneNumber());

        //验证用户密码
        Authentication authenticate = null;
        try {
            authenticate = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, userLoginFrom.getPassword()));
        } catch (AuthenticationException e) {
            log.error("用户登录认证失败", e);
            return Result.failMsg("用户名或密码错误");
        } catch (Exception e) {
            log.error("用户登录异常", e);
            return Result.failMsg("系统异常，请稍后再试");
        }
        SecurityContextHolder.getContext().setAuthentication(authenticate);

        String token = jwtUtil.generatorToken(username);
        log.info("用户登录成功, phone: {}", userLoginFrom.getPhoneNumber().replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
        return Result.ok(token);
    }

    @Operation(summary = "用户注册", description = "用户通过手机号、密码和验证码注册新账号，注册成功返回JWT令牌")
    @PostMapping("/user/register")
    public Result<String> register(@RequestBody @Valid UserRegisterFrom userRegisterFrom) {
        String key = String.format("%s:%s", DefaultConstant.Captcha_Code, userRegisterFrom.getPhoneNumber());
        String code = redisTemplate.opsForValue().get(key);
        if (StringUtils.hasText(code) && userRegisterFrom.getCode().equals(code)) {
            if (usersService.registerUser(userRegisterFrom)) {
                redisTemplate.opsForValue().getAndDelete(key);

                //注册成功后，生成token给用户
                String username = String.format("%s-%s", DefaultConstant.USER_LOGIN_TYPE, userRegisterFrom.getPhoneNumber());
                log.info("用户注册成功, phone: {}", userRegisterFrom.getPhoneNumber().replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
                return Result.ok(jwtUtil.generatorToken(username));
            }
        } else {
            log.warn("验证码验证失败, phone: {}", userRegisterFrom.getPhoneNumber().replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
            return Result.failMsg("验证码过期，请重新获取");
        }
        return Result.ok(null);
    }

    @Operation(summary = "获取短信验证码", description = "向指定手机号发送6位数字短信验证码")
    @GetMapping("/user/captcha/{phoneNumber}")
    public Result<String> getCaptcha(@PathVariable("phoneNumber") @Parameter(description = "手机号码", required = true, example = "13800138000") String phoneNumber) {
        if (!phoneNumber.matches(PHONE_REGEX)) {
            log.warn("验证码请求手机号格式错误, phone: {}", phoneNumber);
            return Result.failMsg("手机号格式错误");
        }
        // 不区分是否已注册，防止手机号枚举
        String key = String.format("%s:%s", DefaultConstant.Captcha_Code, phoneNumber);

        StringBuilder otp = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < CAPTCHA_LENGTH; i++) {
            otp.append(random.nextInt(10)); // 生成 0-9 之间的随机数
        }
        redisTemplate.opsForValue().set(key, otp.toString(), DefaultConstant.Captcha_Timeout, TimeUnit.MILLISECONDS);
        log.info("验证码已生成, phone: {}", phoneNumber.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
        return Result.ok(otp.toString());
    }
}
