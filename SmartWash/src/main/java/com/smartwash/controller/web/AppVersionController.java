package com.smartwash.controller.web;

import com.alibaba.fastjson2.JSON;
import com.smartwash.common.Result;
import com.smartwash.vo.AppVersionVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 应用版本更新接口
 * <p>
 * App 启动时调用 {@code GET /web/app/version} 检查更新。
 * 版本信息从服务器磁盘的 version.json 现读现返，不做缓存，确保发版后立即生效。
 * </p>
 */
@Tag(name = "用户端-应用版本", description = "App 版本更新检查接口")
@Slf4j
@RestController
@RequestMapping("/web")
public class AppVersionController {

    /** 版本文件路径，可通过配置文件覆盖；默认 /opt/smartwash/releases/latest/version.json */
    @Value("${app.version.file-path:/opt/smartwash/releases/latest/version.json}")
    private String versionFilePath;

    /**
     * 获取最新版本信息
     *
     * @return 版本信息 VO，文件不存在或解析失败时返回失败提示
     */
    @Operation(summary = "获取最新版本信息", description = "App 启动时检查更新，现读现返 version.json，无缓存")
    @GetMapping("/app/version")
    public Result<AppVersionVo> getLatestVersion() {
        Path path = Paths.get(versionFilePath);

        // 文件不存在时返回提示
        if (!Files.exists(path)) {
            log.warn("版本文件不存在：{}", versionFilePath);
            return Result.failMsg("暂无版本信息");
        }

        try {
            // 读取文件内容并解析
            String content = Files.readString(path);
            AppVersionVo vo = JSON.parseObject(content, AppVersionVo.class);
            log.debug("读取版本文件成功：versionCode={}, versionName={}", vo.getVersionCode(), vo.getVersionName());
            return Result.ok(vo);
        } catch (Exception e) {
            log.error("读取或解析版本文件失败：{}", versionFilePath, e);
            return Result.failMsg("版本信息读取失败");
        }
    }
}
