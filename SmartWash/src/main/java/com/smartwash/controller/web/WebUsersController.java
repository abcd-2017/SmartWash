package com.smartwash.controller.web;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.DefaultConstant;
import com.smartwash.config.MinioConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.common.Result;
import com.smartwash.entity.Users;
import com.smartwash.from.users.ChangePasswordFrom;
import com.smartwash.from.users.UpdateUserInfo;
import com.smartwash.service.IUsersService;
import com.smartwash.service.FileStorageService;
import com.smartwash.utils.ImageUploadValidator;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import com.smartwash.vo.users.UserInfoVo;
import com.smartwash.vo.users.TransactionVo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

@Tag(name = "用户端-用户信息", description = "用户端个人信息管理接口")
@Slf4j
@RestController
@RequestMapping("/web")
public class WebUsersController {
    @Autowired
    private IUsersService usersService;
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private MinioConfig minioConfig;

    @Operation(summary = "更新用户信息", description = "更新当前用户的学校和学号信息")
    @PostMapping("/auth/user/updateUserInfo")
    public Result<String> updateUserInfo(@RequestBody @Valid UpdateUserInfo updateUserInfo) {
        LoginUser user = UserContextHolder.getUser();
        if (!Objects.equals(user.getUserType(), DefaultConstant.USER_LOGIN_TYPE) || user.getUserId() == null) {
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
        if (users == null) {
            return Result.failMsg("用户不存在");
        }
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
    @PostMapping("/auth/user/bindCampus/{campusCard}")
    public Result<Boolean> bindCampus(@PathVariable("campusCard") @Parameter(description = "校园卡号", required = true, example = "C2021001") String campusCard) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(usersService.bindCampus(campusCard, user.getUserId()));
    }

    @Operation(summary = "解绑校园卡", description = "解除当前用户的校园卡绑定")
    @PostMapping("/auth/user/unBindCampus")
    public Result<Boolean> unBindCampus() {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(usersService.unBindCampus(user.getUserId()));
    }

    @Operation(summary = "获取交易记录", description = "分页获取当前用户的充值和消费记录（page 默认 1，pageSize 默认 10、上限 50）")
    @GetMapping("/auth/user/transactions")
    public Result<Page<TransactionVo>> getTransactions(
            @RequestParam(value = "page", defaultValue = "1") @Parameter(description = "页码，从 1 开始", example = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") @Parameter(description = "每页条数，最大 50", example = "10") Integer pageSize) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(usersService.getTransactionHistory(user.getUserId(), page, pageSize));
    }

    @Operation(summary = "修改密码", description = "当前登录用户校验原密码后修改为新密码（新密码 6-16 位，与注册/重置口径一致）")
    @PutMapping("/auth/user/password")
    public Result<String> changePassword(@RequestBody @Valid ChangePasswordFrom changePasswordFrom) {
        LoginUser user = UserContextHolder.getUser();
        if (!Objects.equals(user.getUserType(), DefaultConstant.USER_LOGIN_TYPE) || user.getUserId() == null) {
            return Result.failMsg("系统异常");
        }
        if (!usersService.changePassword(changePasswordFrom, user.getUserId())) {
            // 业务失败走统一 FAIL 信封（201），不用 401（登录态本身有效，仅原密码校验未通过）
            return Result.failMsg("原密码不正确");
        }
        return Result.ok("密码修改成功");
    }

    /** 头像大小上限 5MB（与 spring.servlet.multipart 配置双保险，代码层兜底防配置回退） */
    private static final long MAX_AVATAR_SIZE = 5L * 1024 * 1024;

    @Operation(summary = "上传头像", description = "上传或更新当前用户的头像图片（仅支持 JPG/PNG/WebP，校验文件魔数与扩展名）")
    @PostMapping("/auth/user/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        // 验证文件大小（5MB，代码层兜底）
        if (file.getSize() > MAX_AVATAR_SIZE) {
            return Result.failMsg("图片大小不能超过5MB");
        }

        // 验证文件真实内容：魔数 + 扩展名一致性，仅信 contentType 可被伪造（评审报告后端 #23）；
        // 委托通用图片校验器，与菜谱封面/工作台配图/游戏封面同口径
        String invalidReason = ImageUploadValidator.validateContent(file);
        if (invalidReason != null) {
            return Result.failMsg(invalidReason);
        }

        LoginUser loginUser = UserContextHolder.getUser();
        Users user = usersService.getById(loginUser.getUserId());
        if (user == null) {
            return Result.failMsg("用户不存在");
        }

        // 删除旧头像（如果不是默认头像）
        if (user.getAvatar() != null && !user.getAvatar().contains("default/avatar.png")) {
            fileStorageService.deleteFile(user.getAvatar());
        }

        // 上传新头像：DB 存对象键（端点变更不必刷库），响应仍返回绝对 URL 供端上立即展示
        String avatarObjectKey = fileStorageService.uploadFile(file, "avatar");

        user.setAvatar(avatarObjectKey);
        usersService.updateById(user);

        return Result.ok(minioConfig.urlOf(avatarObjectKey));
    }

}
