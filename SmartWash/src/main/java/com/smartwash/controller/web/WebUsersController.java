package com.smartwash.controller.web;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.DefaultConstant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.common.Result;
import com.smartwash.entity.Users;
import com.smartwash.from.users.UpdateUserInfo;
import com.smartwash.service.IUsersService;
import com.smartwash.service.FileStorageService;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import com.smartwash.vo.users.UserInfoVo;
import com.smartwash.vo.users.TransactionVo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
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
    private FileStorageService fileStorageService;

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

    @Operation(summary = "获取交易记录", description = "分页获取当前用户的充值和消费记录（page 默认 1，pageSize 默认 10、上限 50）")
    @GetMapping("/auth/user/transactions")
    public Result<Page<TransactionVo>> getTransactions(
            @RequestParam(value = "page", defaultValue = "1") @Parameter(description = "页码，从 1 开始", example = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") @Parameter(description = "每页条数，最大 50", example = "10") Integer pageSize) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(usersService.getTransactionHistory(user.getUserId(), page, pageSize));
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

        // 验证文件真实内容：魔数 + 扩展名一致性，仅信 contentType 可被伪造（评审报告后端 #23）
        String invalidReason = validateImageContent(file);
        if (invalidReason != null) {
            return Result.failMsg(invalidReason);
        }

        // 获取当前用户
        LoginUser loginUser = UserContextHolder.getUser();
        Users user = usersService.getById(loginUser.getUserId());
        if (user == null) {
            return Result.failMsg("用户不存在");
        }

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

    /**
     * 校验上传文件的真实图片类型（评审报告后端 #23）：
     * 读文件头前 12 字节判断魔数（JPEG: FF D8 FF / PNG: 89 50 4E 47 / WebP: RIFF....WEBP），
     * 并要求扩展名与魔数识别出的类型一致，防止改扩展名上传非图片（如 HTML/脚本）内容。
     *
     * @return null=校验通过；否则返回失败原因文案
     */
    private String validateImageContent(MultipartFile file) {
        // 1. 扩展名白名单（统一小写比对）
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.lastIndexOf('.') >= 0) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        }
        boolean extJpeg = "jpg".equals(extension) || "jpeg".equals(extension);
        if (!extJpeg && !"png".equals(extension) && !"webp".equals(extension)) {
            return "仅支持 JPG/PNG/WebP 格式图片";
        }

        // 2. 读取文件头 12 字节判断魔数
        byte[] header = new byte[12];
        int read;
        try (InputStream is = file.getInputStream()) {
            read = is.readNBytes(header, 0, header.length);
        } catch (IOException e) {
            log.warn("头像文件读取失败", e);
            return "图片读取失败，请重试";
        }
        if (read < header.length) {
            return "图片内容不合法";
        }

        // 3. 魔数与扩展名必须一致
        if (isJpeg(header)) {
            return extJpeg ? null : "图片内容与扩展名不符";
        }
        if (isPng(header)) {
            return "png".equals(extension) ? null : "图片内容与扩展名不符";
        }
        if (isWebp(header)) {
            return "webp".equals(extension) ? null : "图片内容与扩展名不符";
        }
        return "仅支持 JPG/PNG/WebP 格式图片";
    }

    /** JPEG 魔数：FF D8 FF */
    private static boolean isJpeg(byte[] b) {
        return (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    /** PNG 魔数：89 50 4E 47 */
    private static boolean isPng(byte[] b) {
        return (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    /** WebP 魔数：第 0-3 字节为 RIFF，第 8-11 字节为 WEBP */
    private static boolean isWebp(byte[] b) {
        return b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }
}
