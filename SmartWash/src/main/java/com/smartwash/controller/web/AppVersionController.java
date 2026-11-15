package com.smartwash.controller.web;

import com.smartwash.common.Result;
import com.smartwash.service.AppVersionService;
import com.smartwash.vo.ChannelVersionVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用版本更新接口
 * <p>
 * App 启动时调用 {@code GET /web/app/version?channel=full|lite} 检查更新。
 * 版本信息从服务器磁盘的 version.json 现读现返，不做缓存，确保发版后立即生效。
 * </p>
 * <p>
 * 两个 Android App（full {@code com.smartwash} / lite {@code com.todayeat.app}）共用本接口，
 * 按 channel 下发各自的 APK 产物，避免互推错包；channel 缺省为 full（旧端不传参行为不变）。
 * </p>
 */
@Tag(name = "用户端-应用版本", description = "App 版本更新检查接口")
@Slf4j
@RestController
@RequestMapping("/web")
@RequiredArgsConstructor
public class AppVersionController {

    private final AppVersionService appVersionService;

    /**
     * 获取指定渠道的最新版本信息
     *
     * @param channel 渠道标识（full / lite），缺省 full
     * @return 该渠道的版本信息（扁平结构），渠道不存在或文件缺失时返回失败提示
     */
    @Operation(summary = "获取最新版本信息", description = "App 启动时检查更新，现读现返 version.json；channel 缺省 full，可选 lite（吃啥）")
    @GetMapping("/app/version")
    public Result<ChannelVersionVo> getLatestVersion(
            @Parameter(description = "渠道：full（全量版，缺省）/ lite（吃啥精简版）")
            @RequestParam(value = "channel", required = false) String channel) {
        return Result.ok(appVersionService.resolveByVersionChannel(channel));
    }
}
