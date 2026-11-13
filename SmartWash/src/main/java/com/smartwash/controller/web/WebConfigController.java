package com.smartwash.controller.web;

import com.smartwash.common.Result;
import com.smartwash.config.MinioConfig;
import com.smartwash.toolbox.toolkit.PublicBaseUrlResolver;
import com.smartwash.toolbox.toolkit.WebBaseUrlResolver;
import com.smartwash.vo.ServerConfigVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务端公开配置接口（App 启动拉取）。
 *
 * <p>历史做法是把 MinIO endpoint 经 Gradle 编译进 APK（BuildConfig.MINIO_ENDPOINT），
 * 缺点是域名变更必须重新发版，且端上无法感知后端实际下发的游戏直链 / 头像地址域名。
 * 现改为运行期下发：端上启动拉一次，WebView 主题桥白名单用运行期值。</p>
 *
 * <p>安全：公开只读（SecurityConfig 已放行 /web/config），不含任何凭据。</p>
 */
@Tag(name = "用户端-服务端配置", description = "App 启动拉取公开配置（MinIO 端点等），替代编译期注入")
@Slf4j
@RestController
@RequestMapping("/web")
public class WebConfigController {

    private final MinioConfig minioConfig;
    /** 短链对外跳转域名，与 ToolboxShortCodeServiceImpl 同源（显式配置优先，否则按请求推导） */
    private final PublicBaseUrlResolver publicBaseUrlResolver;
    /** Web 前端对外地址（私密文本分享链接来源，只认显式配置） */
    private final WebBaseUrlResolver webBaseUrlResolver;

    public WebConfigController(MinioConfig minioConfig, PublicBaseUrlResolver publicBaseUrlResolver,
                               WebBaseUrlResolver webBaseUrlResolver) {
        this.minioConfig = minioConfig;
        this.publicBaseUrlResolver = publicBaseUrlResolver;
        this.webBaseUrlResolver = webBaseUrlResolver;
    }

    @Operation(summary = "获取服务端公开配置",
            description = "MinIO 对外端点 + 短链对外域名 + Web 对外地址；App 启动拉取，替代编译期注入")
    @GetMapping("/config")
    public Result<ServerConfigVo> config(HttpServletRequest request) {
        ServerConfigVo vo = new ServerConfigVo();
        // 对外可访问端点：externalEndpoint 优先（缺失回退 endpoint），与游戏直链/头像同一取值口径
        vo.setMinioEndpoint(minioConfig.publicEndpoint());
        // 按本次请求推导，保证 App 拿到的与它实际访问后端用的域名一致
        vo.setShortLinkBaseUrl(publicBaseUrlResolver.resolve(request));
        // Web SPA 对外地址：无法按请求推导（与后端不同源），未配置返回空串由端上降级
        vo.setWebBaseUrl(webBaseUrlResolver.resolve());
        return Result.ok(vo);
    }
}
