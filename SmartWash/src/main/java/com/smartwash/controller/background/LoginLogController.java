package com.smartwash.controller.background;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.from.login_log.SearchLoginLogFrom;
import com.smartwash.service.ILoginLogService;
import com.smartwash.vo.login_log.LoginLogVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录日志管理控制器。
 *
 * <p>查询接口，无写操作，按规范不加 {@code @Audited}；路径在 {@code /admin/**} 下，
 * 由 SecurityConfig 统一要求 ROLE_ADMIN。
 */
@Tag(name = "登录日志管理", description = "后台登录日志分页查询")
@Slf4j
@RestController
@RequestMapping("/admin/login-logs")
@RequiredArgsConstructor
public class LoginLogController {

    private final ILoginLogService loginLogService;

    @Operation(summary = "分页查询登录日志", description = "按账号/平台/渠道/结果/时间范围分页查询登录日志，按登录时间倒序")
    @GetMapping("/page")
    public Result<Page<LoginLogVo>> page(SearchLoginLogFrom from) {
        return Result.ok(loginLogService.pageLoginLogs(from));
    }
}
