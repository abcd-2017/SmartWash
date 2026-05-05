package com.smartwash.controller.web;


import com.smartwash.common.DefaultConstant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.common.Result;
import com.smartwash.entity.Users;
import com.smartwash.from.users.UpdateUserInfo;
import com.smartwash.service.IUsersService;
import com.smartwash.service.FileStorageService;
import com.smartwash.utils.JwtUtil;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import com.smartwash.vo.users.UserInfoVo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@Tag(name = "用户端-用户信息", description = "用户端个人信息管理接口")
@Slf4j
@RestController
@RequestMapping("/web")
public class WebUsersController {
    @Autowired
    private IUsersService usersService;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private FileStorageService fileStorageService;

    @Operation(summary = "更新用户信息", description = "更新当前用户的学校和学号信息")
    @PostMapping("/auth/user/updateUserInfo")
    public Result<String> updateUserInfo(@RequestBody @Valid UpdateUserInfo updateUserInfo) {
        LoginUser user = UserContextHolder.getUser();
        if (!Objects.equals(user.getUserType(), DefaultConstant.USER_LOGIN_TYPE) && user.getUserId() == null) {
            return Result.failMsg("系统异常");
        }
        usersService.updateUserInfo(updateUserInfo, user.getUserId());
        return Result.ok("修改成功");
    }

    @Operation(summary = "获取用户学校ID", description = "获取当前用户所属学校的ID")
    @GetMapping("/auth/user/school")
    public Result<Long> getUserSchoolId() {
        LoginUser user = UserContextHolder.getUser();
        Users users = usersService.getById(user.getUserId());
        return Result.ok(users.getSchoolId());
    }

    @Operation(summary = "检查学号是否已注册", description = "检查指定学号是否已被其他用户注册")
    @GetMapping("/auth/user/getUserByStudentId")
    public Result<Boolean> getUserByStudentId(@RequestParam("studentId") @Parameter(description = "学号", required = true, example = "2021001") String studentId) {
        Users users = usersService.getUserByStudentId(studentId);
        return Result.ok(users == null);
    }

    @Operation(summary = "获取用户信息", description = "获取当前用户的详细信息，包括学校、学号、余额等")
    @GetMapping("/auth/user/getUserInfo")
    public Result<UserInfoVo> getUserInfo() {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(usersService.getUserInfo(user.getUserId()));
    }

    @Operation(summary = "绑定校园卡", description = "为当前用户绑定校园卡")
    @PostMapping("/auth/user/bingCampus/{campusCard}")
    public Result<Boolean> bingCampus(@PathVariable("campusCard") @Parameter(description = "校园卡号", required = true, example = "C2021001") String campusCard) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(usersService.bingCampus(campusCard, user.getUserId()));
    }

    @Operation(summary = "解绑校园卡", description = "解除当前用户的校园卡绑定")
    @PostMapping("/auth/user/unBingCampus")
    public Result<Boolean> unBingCampus() {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(usersService.unBingCampus(user.getUserId()));
    }

    @Operation(summary = "上传头像", description = "上传或更新当前用户的头像图片")
    @PostMapping("/auth/user/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        // 验证文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.failMsg("只能上传图片文件");
        }

        // 验证文件大小（5MB）
        if (file.getSize() > 5 * 1024 * 1024) {
            return Result.failMsg("图片大小不能超过5MB");
        }

        // 获取当前用户
        LoginUser loginUser = UserContextHolder.getUser();
        Users user = usersService.getById(loginUser.getUserId());

        // 删除旧头像（如果不是默认头像）
        if (user.getAvatar() != null && !user.getAvatar().contains("default/avatar.png")) {
            fileStorageService.deleteFile(user.getAvatar());
        }

        // 上传新头像
        String avatarUrl = fileStorageService.uploadFile(file, "avatar");

        // 更新用户头像
        user.setAvatar(avatarUrl);
        usersService.updateById(user);

        return Result.ok(avatarUrl);
    }
}
