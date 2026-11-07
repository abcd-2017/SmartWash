package com.smartwash.game.support;

import com.smartwash.exception.GameServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 游戏上传包校验器。
 * 上限（方案 §四）：原始文件 ≤10MB；zip 解压条目 ≤200、解压总字节 ≤20MB（防 zip 炸弹，边解压边计量）。
 * ZipSlip 防护：条目落盘前做 canonical path 前缀校验（参考 PsiTransfer isSafeBasename 思路，
 * 调研文档 research/01 §2.5），相对路径必须落在临时解压目录内。
 * 内容安全（2026-11-07 MVP，调研 docs/游戏库调研-HTML上传安全审计-2026-11-06.md）：
 * ① html 条目静态黑名单扫描——禁外联/协议相对外链/javascript: 伪协议/内联事件属性/
 *   eval·new Function·document.write/iframe·object·embed·applet/meta refresh；
 * ② 入口 index.html 注入 meta CSP（connect-src 'none' 等），WebView 直拉时禁一切外发。
 * 与库内零外链红线（theme-gates）同口径——库内 31 款种子游戏全部通过本扫描。
 */
@Slf4j
@Component
public class GamePackageValidator {

    /** 原始上传文件上限：10MB */
    public static final long MAX_ORIGINAL_BYTES = 10L * 1024 * 1024;
    /** zip 解压条目上限：200 个 */
    public static final int MAX_ZIP_ENTRIES = 200;
    /** zip 解压后总字节上限：20MB */
    public static final long MAX_TOTAL_UNCOMPRESSED = 20L * 1024 * 1024;

    /** 解压结果：条目数与解压后总字节（供 file_size 落库） */
    public record ExtractResult(int fileCount, long totalBytes) {
    }

    /** 允许的上传扩展名白名单（大小写不敏感） */
    public boolean isAllowedExtension(String filename) {
        if (filename == null) {
            return false;
        }
        String name = filename.toLowerCase(Locale.ROOT);
        return name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".zip");
    }

    /**
     * 校验原始上传文件：非空、扩展名白名单、大小 ≤10MB。
     */
    public void validateOriginalFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new GameServiceException(GameServiceException.BAD_REQUEST, "上传文件不能为空");
        }
        if (!isAllowedExtension(file.getOriginalFilename())) {
            throw new GameServiceException(GameServiceException.BAD_REQUEST, "仅支持 .html / .htm / .zip 文件");
        }
        if (file.getSize() > MAX_ORIGINAL_BYTES) {
            throw new GameServiceException(GameServiceException.PAYLOAD_TOO_LARGE,
                    "上传文件超过 10MB 上限");
        }
    }

    /**
     * 解压 zip 到目标目录（须已存在），含防炸弹与 ZipSlip 校验。
     * 目录条目不计数；文件按「边读边计」防止声明尺寸造假绕过限制。
     *
     * @param zipStream zip 输入流（本方法不关闭调用方流）
     * @param targetDir 解压目标目录
     */
    public ExtractResult extractZip(InputStream zipStream, Path targetDir) {
        Path absoluteTarget = toCanonicalDir(targetDir);
        int fileCount = 0;
        long totalBytes = 0;
        try (ZipInputStream zin = new ZipInputStream(zipStream, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zin.closeEntry();
                    continue;
                }
                if (++fileCount > MAX_ZIP_ENTRIES) {
                    throw new GameServiceException(GameServiceException.PAYLOAD_TOO_LARGE,
                            "压缩包内文件数超过 200 个上限");
                }
                Path dest = safeResolve(absoluteTarget, entry.getName());
                totalBytes += copyEntry(zin, dest);
                if (totalBytes > MAX_TOTAL_UNCOMPRESSED) {
                    throw new GameServiceException(GameServiceException.PAYLOAD_TOO_LARGE,
                            "压缩包解压后超过 20MB 上限");
                }
                zin.closeEntry();
            }
        } catch (GameServiceException e) {
            throw e;
        } catch (IOException e) {
            log.error("游戏 zip 解压失败", e);
            throw new GameServiceException(GameServiceException.BAD_REQUEST, "压缩包解析失败，请确认文件完整");
        }
        if (fileCount == 0) {
            throw new GameServiceException(GameServiceException.BAD_REQUEST, "压缩包内没有文件");
        }
        return new ExtractResult(fileCount, totalBytes);
    }

    /**
     * 探测包内入口相对路径：优先根目录 index.html，其次根目录（一级）任意 .html；找不到拒绝上传。
     */
    public String resolveEntryPath(Path rootDir) {
        Path root = toCanonicalDir(rootDir);
        Path index = root.resolve("index.html");
        if (Files.isRegularFile(index)) {
            return "index.html";
        }
        try (var stream = Files.list(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".html") || n.toLowerCase(Locale.ROOT).endsWith(".htm"))
                    .sorted()
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ── 内容安全：静态黑名单扫描 + 入口 meta CSP 注入 ──

    /** CSP 策略：单文件游戏仅需内联脚本/样式与 data/blob 资源；禁一切外发（connect-src 'none'）、
     * 嵌套（frame-src/object-src 'none'）与基址劫持（base-uri/form-action 'none'） */
    public static final String CSP_META_CONTENT = "default-src 'none'; script-src 'unsafe-inline'; "
            + "style-src 'unsafe-inline'; img-src data: blob:; media-src data: blob:; font-src data:; "
            + "connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'";

    /** 黑名单规则：命中即拒绝上传（message 定位到规则名，不回显命中内容防注入日志） */
    private static final java.util.List<java.util.regex.Pattern> BLACKLIST = java.util.List.of(
            java.util.regex.Pattern.compile("https?://"),
            java.util.regex.Pattern.compile("(?:src|href)\\s*=\\s*[\"']//"),
            java.util.regex.Pattern.compile("javascript:"),
            // 内联事件属性：限定属性位（前导空白/引号），避免误伤 JS 变量名（如 PAL.onPrimary=）
            java.util.regex.Pattern.compile("(?:^|[\\s\"'/])on[a-z]+\\s*=", java.util.regex.Pattern.CASE_INSENSITIVE),
            java.util.regex.Pattern.compile("eval\\s*\\(|new\\s+Function\\s*\\(|document\\.write\\s*\\("),
            java.util.regex.Pattern.compile("<iframe|<object|<embed|<applet", java.util.regex.Pattern.CASE_INSENSITIVE),
            java.util.regex.Pattern.compile("http-equiv\\s*=\\s*[\"']?refresh", java.util.regex.Pattern.CASE_INSENSITIVE)
    );
    private static final java.util.List<String> BLACKLIST_NAMES = java.util.List.of(
            "外部链接（https?://）", "协议相对外部资源（//host）", "javascript: 伪协议",
            "内联事件属性（on*=）", "动态代码执行（eval/new Function/document.write）",
            "嵌入元素（iframe/object/embed/applet）", "meta refresh 跳转");

    /**
     * html 内容静态黑名单扫描（上传安全 MVP）：任一规则命中即 403 拒绝上传。
     * 与库内零外链红线同口径；黑名单校验能力以实际拦截为准，不依赖正则覆盖全部攻击面。
     */
    public void scanHtmlContent(String content) {
        for (int i = 0; i < BLACKLIST.size(); i++) {
            if (BLACKLIST.get(i).matcher(content).find()) {
                throw new GameServiceException(GameServiceException.FORBIDDEN,
                        "上传内容包含不允许的行为：" + BLACKLIST_NAMES.get(i));
            }
        }
    }

    /**
     * 解压包内容扫描：遍历全部 html/htm 条目逐文件扫描（非 html 资源不扫——
     * 图片/音频等内容型文件由 MIME 白名单与 WebView 加载端约束兜底）。
     */
    public void scanExtractedPackage(Path rootDir) {
        Path root = toCanonicalDir(rootDir);
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".html") || n.endsWith(".htm");
                    })
                    .forEach(p -> {
                        try {
                            scanHtmlContent(Files.readString(p, StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            throw new GameServiceException(GameServiceException.BAD_REQUEST,
                                    "压缩包含无法读取的 html 文件: " + root.relativize(p));
                        }
                    });
        } catch (GameServiceException e) {
            throw e;
        } catch (IOException e) {
            log.error("游戏包内容扫描失败", e);
            throw new GameServiceException(GameServiceException.BAD_REQUEST, "压缩包解析失败，请稍后重试");
        }
    }

    /** 纯变换：html 文本追加 CSP meta（幂等：已含 Content-Security-Policy 则原样返回） */
    public String applyCspMarkup(String html) {
        String lower = html.toLowerCase(Locale.ROOT);
        if (lower.contains("http-equiv=\"content-security-policy\"")
                || lower.contains("http-equiv='content-security-policy'")) {
            return html;
        }
        String meta = "<meta http-equiv=\"Content-Security-Policy\" content=\"" + CSP_META_CONTENT + "\">";
        java.util.regex.Matcher head = java.util.regex.Pattern.compile("(?i)(<head[^>]*>)").matcher(html);
        if (head.find()) {
            return html.substring(0, head.end()) + meta + html.substring(head.end());
        }
        java.util.regex.Matcher htmlTag = java.util.regex.Pattern.compile("(?i)(<html[^>]*>)").matcher(html);
        if (htmlTag.find()) {
            return html.substring(0, htmlTag.end()) + meta + html.substring(htmlTag.end());
        }
        return meta + html;
    }

    /**
     * 入口 html 注入 meta CSP（读文件 → applyCspMarkup → 写回；幂等）。
     * 注入失败不阻断上传（CSP 是纵深防御一层，上传扫描与 WebView 侧约束仍生效），仅 warn 留痕。
     */
    public void injectEntryCsp(Path rootDir, String entryPath) {
        try {
            Path entry = toCanonicalDir(rootDir).resolve(entryPath);
            String replaced = applyCspMarkup(Files.readString(entry, StandardCharsets.UTF_8));
            Files.writeString(entry, replaced, StandardCharsets.UTF_8);
            log.info("入口 {} 已注入 meta CSP", entryPath);
        } catch (IOException e) {
            log.warn("入口 CSP 注入失败（跳过，不阻断上传）: {}", e.getMessage());
        }
    }

    /** ZipSlip 防护：规范化 + canonical path 前缀校验，越界抛 403 */
    private Path safeResolve(Path absoluteTarget, String entryName) {
        // 反斜杠统一为斜杠（Windows 打包的 zip 目录分隔符），并拒绝绝对路径与盘符
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains(":")) {
            throw new GameServiceException(GameServiceException.FORBIDDEN, "压缩包含非法路径条目: " + entryName);
        }
        Path resolved = absoluteTarget.resolve(normalized).normalize();
        if (!resolved.startsWith(absoluteTarget)) {
            throw new GameServiceException(GameServiceException.FORBIDDEN, "压缩包含路径穿越条目: " + entryName);
        }
        // canonical 校验兜底（防符号链接等文件系统层面绕过）
        try {
            String canonical = resolved.toFile().getCanonicalPath();
            if (!canonical.startsWith(absoluteTarget.toString())) {
                throw new GameServiceException(GameServiceException.FORBIDDEN, "压缩包含路径穿越条目: " + entryName);
            }
            return resolved;
        } catch (IOException e) {
            throw new GameServiceException(GameServiceException.BAD_REQUEST, "压缩包解析失败，请确认文件完整");
        }
    }

    /** 边读边计量写盘，防声明尺寸造假；累计超限即抛 413 */
    private long copyEntry(ZipInputStream zin, Path dest) throws IOException {
        Files.createDirectories(dest.getParent());
        long written = 0;
        byte[] buffer = new byte[8192];
        try (java.io.OutputStream out = Files.newOutputStream(dest)) {
            int n;
            while ((n = zin.read(buffer)) != -1) {
                written += n;
                if (written > MAX_TOTAL_UNCOMPRESSED) {
                    throw new GameServiceException(GameServiceException.PAYLOAD_TOO_LARGE,
                            "压缩包解压后超过 20MB 上限");
                }
                out.write(buffer, 0, n);
            }
        }
        return written;
    }

    private Path toCanonicalDir(Path dir) {
        try {
            return Path.of(dir.toFile().getCanonicalPath());
        } catch (IOException e) {
            throw new GameServiceException(GameServiceException.BAD_REQUEST, "临时目录不可用，请稍后重试");
        }
    }

    /** 统计目录下文件数与总字节（供测试与外部分析复用） */
    public static long[] statFiles(Path rootDir) throws IOException {
        final long[] stat = new long[2];
        Files.walkFileTree(rootDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                stat[0]++;
                stat[1] += attrs.size();
                return FileVisitResult.CONTINUE;
            }
        });
        return stat;
    }
}
