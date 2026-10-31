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
