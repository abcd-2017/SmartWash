package com.smartwash.toolbox.listener;

import com.smartwash.toolbox.toolkit.ClientIpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;

/**
 * 短码访问事件。
 * 构造时即抽取请求字段：@Async 监听器运行在另一线程，之后再读已回收的 request 对象存在数据竞争。
 */
@Getter
public class ShortCodeVisitedEvent {

    private final String code;

    /** GET / HEAD 等，HEAD 不计数（shlink 统计卫生） */
    private final String method;

    private final String userAgent;

    private final String referer;

    private final String clientIp;

    public ShortCodeVisitedEvent(String code, HttpServletRequest request) {
        this.code = code;
        this.method = request.getMethod();
        this.userAgent = request.getHeader("User-Agent");
        this.referer = request.getHeader("Referer");
        this.clientIp = ClientIpUtil.get(request);
    }
}
