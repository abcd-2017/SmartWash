package com.smartwash.readlater.toolkit;

import com.smartwash.divination.support.DivUrlSafetyValidator;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * 页面标题抓取器（Jsoup）。
 *
 * 安全与健壮性约定：
 *   - 抓取前必须过 {@link DivUrlSafetyValidator}（仅 https、拒内网/环回，SSRF fail-closed）；
 *   - {@code followRedirects(false)}：校验只覆盖首跳，禁止跟随重定向绕过校验（GLOSSARY §6.4）；
 *   - 连接/读取超时各 5s（Jsoup timeout 语义：建连与 socket 读共用该上限）；
 *   - 任何失败（校验不过/网络异常/解析异常/标题为空）返回 null，由调用方兜底，绝不阻塞入库。
 */
@Slf4j
@Component
public class PageTitleFetcher {

    /** 建连与读取超时（ms）：Jsoup 的 timeout 同时作用于建连与 socket 读 */
    private static final int TIMEOUT_MS = 5000;

    /** 请求 UA：桌面浏览器，避免部分站点对非常规 UA 返回空页 */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    /**
     * 抓取页面标题。
     *
     * @param url 已入库的收藏链接（须为 https）
     * @return 页面 title（trim 后非空）；任何失败返回 null
     */
    public String fetchTitle(String url) {
        // SSRF fail-closed：校验不过直接不发起请求
        DivUrlSafetyValidator.check(url);
        try {
            Document document = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(false)
                    .ignoreContentType(true)
                    .get();
            String title = normalizeTitle(document.title());
            log.debug("页面标题抓取完成, url长度: {}, 命中标题: {}", url.length(), title != null);
            return title;
        } catch (Exception e) {
            // 抓取失败不阻塞入库：调用方以 url 兜底标题
            log.debug("页面标题抓取失败, 兜底为链接本身, 原因: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 HTML 字符串提取标题（纯函数，供单测与复用；不做任何网络 IO）。
     *
     * @param html 页面 HTML 源码
     * @return title 文本（trim 后非空），否则 null
     */
    public static String extractTitle(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        try {
            return normalizeTitle(Jsoup.parse(html).title());
        } catch (Exception e) {
            // 畸形 HTML 交由 Jsoup 容错，理论上不进这里；防御性兜底
            log.debug("HTML 标题解析异常: {}", e.getMessage());
            return null;
        }
    }

    /** 标题归一：trim，空白视为未命中 */
    private static String normalizeTitle(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
