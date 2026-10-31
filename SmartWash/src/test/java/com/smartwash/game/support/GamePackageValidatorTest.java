package com.smartwash.game.support;

import com.smartwash.exception.GameServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 游戏上传包校验器测试。
 * 覆盖：扩展名白名单、原始大小上限、zip 炸弹（条目数/解压总量）、ZipSlip（../ 与反斜杠与绝对路径）、
 * 入口路径探测（index.html 优先/一级 html/缺入口）。
 * 纯 JUnit：zip 用内存字节构造，解压走真实文件系统临时目录，不依赖任何容器。
 */
@DisplayName("GamePackageValidator 上传包校验测试")
class GamePackageValidatorTest {

    private final GamePackageValidator validator = new GamePackageValidator();

    @TempDir
    Path tempDir;

    // ==================== 原始文件校验 ====================

    @Test
    @DisplayName("扩展名白名单：html/htm/zip 通过，其余拒绝")
    void isAllowedExtension_whitelist() {
        assertTrue(validator.isAllowedExtension("game.html"));
        assertTrue(validator.isAllowedExtension("GAME.HTML"));
        assertTrue(validator.isAllowedExtension("index.htm"));
        assertTrue(validator.isAllowedExtension("pack.zip"));
        assertFalse(validator.isAllowedExtension("evil.exe"));
        assertFalse(validator.isAllowedExtension("noext"));
        assertFalse(validator.isAllowedExtension(null));
    }

    @Test
    @DisplayName("原始文件：空文件与超 10MB 抛业务异常")
    void validateOriginalFile_sizeAndEmpty() {
        GameServiceException empty = assertThrows(GameServiceException.class,
                () -> validator.validateOriginalFile(new MockMultipartFile("file", new byte[0])));
        assertEquals(GameServiceException.BAD_REQUEST, empty.getCode());

        GameServiceException oversized = assertThrows(GameServiceException.class,
                () -> validator.validateOriginalFile(new MockMultipartFile("file", "big.zip",
                        "application/zip", new byte[(int) (GamePackageValidator.MAX_ORIGINAL_BYTES + 1)])));
        assertEquals(GameServiceException.PAYLOAD_TOO_LARGE, oversized.getCode());
        assertTrue(oversized.getMessage().contains("10MB"));
    }

    @Test
    @DisplayName("原始文件：非白名单扩展名拒绝且不触解压")
    void validateOriginalFile_extensionRejected() {
        GameServiceException ex = assertThrows(GameServiceException.class,
                () -> validator.validateOriginalFile(new MockMultipartFile("file", "evil.exe", "application/octet-stream",
                        new byte[]{1})));
        assertEquals(GameServiceException.BAD_REQUEST, ex.getCode());
    }

    // ==================== zip 解压：正常与防炸弹 ====================

    @Test
    @DisplayName("正常 zip：目录+多文件解压成功，返回文件数与总字节")
    void extractZip_normalPackage() throws Exception {
        byte[] zip = zipBytes(Map.of(
                "index.html", "<html>2048</html>".getBytes(StandardCharsets.UTF_8),
                "assets/js/main.js", "console.log(1)".getBytes(StandardCharsets.UTF_8),
                "assets/img/logo.png", new byte[]{(byte) 0x89, 'P', 'N', 'G'}));

        GamePackageValidator.ExtractResult result =
                validator.extractZip(new ByteArrayInputStream(zip), tempDir);

        assertEquals(3, result.fileCount());
        assertEquals(Files.size(tempDir.resolve("index.html"))
                        + Files.size(tempDir.resolve("assets/js/main.js"))
                        + Files.size(tempDir.resolve("assets/img/logo.png")),
                result.totalBytes());
        assertTrue(Files.exists(tempDir.resolve("assets/js/main.js")));
    }

    @Test
    @DisplayName("zip 炸弹-条目数：超过 200 个文件抛 413")
    void extractZip_tooManyEntries() throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        for (int i = 0; i <= GamePackageValidator.MAX_ZIP_ENTRIES; i++) {
            entries.put("f" + i + ".txt", new byte[]{'x'});
        }
        byte[] zip = zipBytes(entries);

        GameServiceException ex = assertThrows(GameServiceException.class,
                () -> validator.extractZip(new ByteArrayInputStream(zip), tempDir));
        assertEquals(GameServiceException.PAYLOAD_TOO_LARGE, ex.getCode());
        assertTrue(ex.getMessage().contains("200"));
    }

    @Test
    @DisplayName("zip 炸弹-解压总量：声明尺寸很小但解压超 20MB，边读边计拦截（413）")
    void extractZip_uncompressedTooLarge() throws Exception {
        // 全零 21MB：压缩后 zip 流极小，模拟声明尺寸造假的高压缩炸弹
        byte[] bigPayload = new byte[(int) (GamePackageValidator.MAX_TOTAL_UNCOMPRESSED + 1024)];
        byte[] zip = zipBytes(Map.of("bomb.txt", bigPayload));

        GameServiceException ex = assertThrows(GameServiceException.class,
                () -> validator.extractZip(new ByteArrayInputStream(zip), tempDir));
        assertEquals(GameServiceException.PAYLOAD_TOO_LARGE, ex.getCode());
        assertTrue(ex.getMessage().contains("20MB"));
    }

    @Test
    @DisplayName("zip 炸弹-解压总量：多个文件累计超限同样拦截")
    void extractZip_uncompressedAccumulatedTooLarge() throws Exception {
        int half = (int) GamePackageValidator.MAX_TOTAL_UNCOMPRESSED / 2 + 1024;
        byte[] zip = zipBytes(Map.of(
                "a.txt", new byte[half],
                "b.txt", new byte[half]));

        GameServiceException ex = assertThrows(GameServiceException.class,
                () -> validator.extractZip(new ByteArrayInputStream(zip), tempDir));
        assertEquals(GameServiceException.PAYLOAD_TOO_LARGE, ex.getCode());
    }

    @Test
    @DisplayName("空 zip（仅目录条目）拒绝")
    void extractZip_emptyPackage() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("only-dir/"));
            zos.closeEntry();
        }
        GameServiceException ex = assertThrows(GameServiceException.class,
                () -> validator.extractZip(new ByteArrayInputStream(bos.toByteArray()), tempDir));
        assertEquals(GameServiceException.BAD_REQUEST, ex.getCode());
    }

    // ==================== ZipSlip ====================

    @Test
    @DisplayName("ZipSlip：../ 相对路径穿越条目拒绝（403）且不落盘")
    void extractZip_zipSlipDotDotRejected() throws Exception {
        byte[] zip = zipBytes(Map.of("../evil.txt", "pwn".getBytes(StandardCharsets.UTF_8)));

        GameServiceException ex = assertThrows(GameServiceException.class,
                () -> validator.extractZip(new ByteArrayInputStream(zip), tempDir));
        assertEquals(GameServiceException.FORBIDDEN, ex.getCode());
        assertFalse(Files.exists(tempDir.getParent().resolve("evil.txt")),
                "穿越目标位置不得出现文件");
    }

    @Test
    @DisplayName("ZipSlip：Windows 反斜杠分隔的穿越条目拒绝（403）")
    void extractZip_zipSlipBackslashRejected() throws Exception {
        byte[] zip = zipBytes(Map.of("..\\evil.txt", "pwn".getBytes(StandardCharsets.UTF_8)));

        GameServiceException ex = assertThrows(GameServiceException.class,
                () -> validator.extractZip(new ByteArrayInputStream(zip), tempDir));
        assertEquals(GameServiceException.FORBIDDEN, ex.getCode());
    }

    @Test
    @DisplayName("ZipSlip：绝对路径与盘符条目拒绝（403）")
    void extractZip_zipSlipAbsoluteRejected() throws Exception {
        assertEquals(GameServiceException.FORBIDDEN, assertThrows(GameServiceException.class,
                () -> validator.extractZip(new ByteArrayInputStream(
                        zipBytes(Map.of("/etc/evil.txt", "p".getBytes(StandardCharsets.UTF_8)))), tempDir)).getCode());
        assertEquals(GameServiceException.FORBIDDEN, assertThrows(GameServiceException.class,
                () -> validator.extractZip(new ByteArrayInputStream(
                        zipBytes(Map.of("C:/evil.txt", "p".getBytes(StandardCharsets.UTF_8)))), tempDir)).getCode());
    }

    // ==================== 入口探测 ====================

    @Test
    @DisplayName("入口探测：根目录 index.html 优先")
    void resolveEntryPath_prefersIndexHtml() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("pkg"));
        Files.writeString(root.resolve("index.html"), "<html>");
        Files.writeString(root.resolve("game.html"), "<html>");

        assertEquals("index.html", validator.resolveEntryPath(root));
    }

    @Test
    @DisplayName("入口探测：无 index 时取根目录一级 html（字典序最小）")
    void resolveEntryPath_fallsBackToFirstRootHtml() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("pkg"));
        Files.writeString(root.resolve("game.html"), "<html>");
        Files.writeString(root.resolve("assets.txt"), "not html");
        Files.createDirectory(root.resolve("sub"));

        assertEquals("game.html", validator.resolveEntryPath(root));
    }

    @Test
    @DisplayName("入口探测：根目录无 html 返回 null（由服务层拒绝上传）")
    void resolveEntryPath_missingEntry() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("pkg"));
        Files.writeString(root.resolve("readme.txt"), "no html here");
        Files.createDirectories(root.resolve("sub"));
        Files.writeString(root.resolve("sub/index.html"), "<html>");

        assertNull(validator.resolveEntryPath(root));
    }

    // ==================== 工具 ====================

    private byte[] zipBytes(Map<String, byte[]> entries) throws java.io.IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }
}
