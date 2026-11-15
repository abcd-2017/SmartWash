package com.smartwash.service.impl;

import com.smartwash.common.AppChannel;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.service.AppVersionService;
import com.smartwash.utils.JsonUtils;
import com.smartwash.vo.AppVersionVo;
import com.smartwash.vo.ChannelVersion;
import com.smartwash.vo.ChannelVersionVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 版本清单按渠道下发单测（纯 POJO + 临时文件，不依赖 Spring 上下文/MySQL/Redis/MinIO）。
 *
 * <p>覆盖：channels 分组解析、channel 缺省 full、lite 独立产物、渠道不存在/产物缺失的失败语义、
 * 旧清单（仅顶层字段）向后兼容。</p>
 */
@DisplayName("AppVersionServiceImpl 按渠道下发版本")
class AppVersionServiceImplTest {

    @TempDir
    Path tempDir;

    private Path versionFile;
    private AppVersionService service;

    @BeforeEach
    void setUp() throws Exception {
        versionFile = tempDir.resolve("version.json");
        service = new AppVersionServiceImpl();
        ReflectionTestUtils.setField(service, "versionFilePath", versionFile.toString());
    }

    private void writeManifest(AppVersionVo vo) throws Exception {
        Files.writeString(versionFile, JsonUtils.toJson(vo));
    }

    private AppVersionVo manifest() {
        AppVersionVo vo = new AppVersionVo();
        vo.setVersionCode(1000000);
        vo.setVersionName("1.0.0");
        vo.setForceUpdate(false);
        vo.setMinVersionCode(1000000);
        vo.setChangelog("首个双渠道版本");
        vo.setReleaseDate("2026-11-15");
        return vo;
    }

    private ChannelVersion artifact(String fileName, long size, String sha) {
        ChannelVersion cv = new ChannelVersion();
        cv.setFileName(fileName);
        cv.setFileSize(size);
        cv.setSha256(sha);
        return cv;
    }

    @Test
    @DisplayName("channel 缺省 = full：不传参取 full 产物，顶层版本字段随之下发")
    void resolve_defaultChannel_isFull() throws Exception {
        AppVersionVo vo = manifest();
        Map<String, ChannelVersion> channels = new LinkedHashMap<>();
        channels.put("full", artifact("app-1.0.0.apk", 123L, "sha-full"));
        channels.put("lite", artifact("app-lite-1.0.0.apk", 456L, "sha-lite"));
        vo.setChannels(channels);
        writeManifest(vo);

        ChannelVersionVo result = service.resolveByVersionChannel(null);

        assertEquals("full", result.getChannel());
        assertEquals("app-1.0.0.apk", result.getFileName());
        assertEquals(123L, result.getFileSize());
        assertEquals("sha-full", result.getSha256());
        assertEquals(1000000, result.getVersionCode());
        assertEquals("1.0.0", result.getVersionName());
        assertEquals("首个双渠道版本", result.getChangelog());
        assertEquals("2026-11-15", result.getReleaseDate());
    }

    @Test
    @DisplayName("channel=lite：取 lite 产物，与 full 互不串包")
    void resolve_liteChannel_liteArtifact() throws Exception {
        AppVersionVo vo = manifest();
        Map<String, ChannelVersion> channels = new LinkedHashMap<>();
        channels.put("full", artifact("app-1.0.0.apk", 123L, "sha-full"));
        channels.put("lite", artifact("app-lite-1.0.0.apk", 456L, "sha-lite"));
        vo.setChannels(channels);
        writeManifest(vo);

        ChannelVersionVo result = service.resolveByVersionChannel("lite");

        assertEquals("lite", result.getChannel());
        assertEquals("app-lite-1.0.0.apk", result.getFileName());
        assertEquals(456L, result.getFileSize());
        assertEquals("sha-lite", result.getSha256());
    }

    @Test
    @DisplayName("channel 大小写不敏感：FULL 等价 full")
    void resolve_channelCaseInsensitive() throws Exception {
        AppVersionVo vo = manifest();
        Map<String, ChannelVersion> channels = new LinkedHashMap<>();
        channels.put("full", artifact("app-1.0.0.apk", 123L, "sha-full"));
        vo.setChannels(channels);
        writeManifest(vo);

        assertEquals("full", service.resolveByVersionChannel("FULL").getChannel());
        assertEquals("full", service.resolveByVersionChannel(" Full ").getChannel());
    }

    @Test
    @DisplayName("顶层与 channels.full 同写：以顶层为 full 事实源（任务约定保留顶层 full 字段）")
    void resolve_fullPrefersTopLevel() throws Exception {
        AppVersionVo vo = manifest();
        vo.setFileName("app-top-1.0.0.apk");
        vo.setFileSize(111L);
        vo.setSha256("sha-top");
        Map<String, ChannelVersion> channels = new LinkedHashMap<>();
        channels.put("full", artifact("app-group-1.0.0.apk", 222L, "sha-group"));
        channels.put("lite", artifact("app-lite-1.0.0.apk", 456L, "sha-lite"));
        vo.setChannels(channels);
        writeManifest(vo);

        assertEquals("app-top-1.0.0.apk", service.resolveByVersionChannel("full").getFileName());
        assertEquals("app-lite-1.0.0.apk", service.resolveByVersionChannel("lite").getFileName());
    }

    @Test
    @DisplayName("顶层缺失时 full 回落 channels.full（兼容只写分组的清单）")
    void resolve_fullFallsBackToGroup() throws Exception {
        AppVersionVo vo = manifest();
        Map<String, ChannelVersion> channels = new LinkedHashMap<>();
        channels.put("full", artifact("app-group-1.0.0.apk", 222L, "sha-group"));
        vo.setChannels(channels);
        writeManifest(vo);

        assertEquals("app-group-1.0.0.apk", service.resolveByVersionChannel("full").getFileName());
    }

    @Test
    @DisplayName("渠道不存在：明确失败提示，不静默回落 full 包")
    void resolve_unknownChannel_fails() throws Exception {
        AppVersionVo vo = manifest();
        Map<String, ChannelVersion> channels = new LinkedHashMap<>();
        channels.put("full", artifact("app-1.0.0.apk", 123L, "sha-full"));
        vo.setChannels(channels);
        writeManifest(vo);

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.resolveByVersionChannel("beta"));
        assertEquals("不支持的更新渠道：beta", ex.getMessage());
    }

    @Test
    @DisplayName("清单缺少 lite 渠道产物：明确失败提示（不推 full 包）")
    void resolve_liteMissingArtifact_fails() throws Exception {
        AppVersionVo vo = manifest();
        Map<String, ChannelVersion> channels = new LinkedHashMap<>();
        channels.put("full", artifact("app-1.0.0.apk", 123L, "sha-full"));
        vo.setChannels(channels);
        writeManifest(vo);

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.resolveByVersionChannel("lite"));
        assertTrue(ex.getMessage().contains("吃啥"), "失败提示应说明是哪个渠道缺失");
    }

    @Test
    @DisplayName("旧清单向后兼容：无 channels 分组时 full 回落顶层 fileName/fileSize/sha256")
    void resolve_legacyManifest_fullFallsBackToTopLevel() throws Exception {
        AppVersionVo vo = manifest();
        vo.setFileName("app-1.0.0.apk");
        vo.setFileSize(789L);
        vo.setSha256("sha-legacy");
        writeManifest(vo);

        ChannelVersionVo result = service.resolveByVersionChannel(null);

        assertEquals("app-1.0.0.apk", result.getFileName());
        assertEquals(789L, result.getFileSize());
        assertEquals("sha-legacy", result.getSha256());
    }

    @Test
    @DisplayName("旧清单向后兼容：lite 无分组也无顶层回落，必须失败")
    void resolve_legacyManifest_liteStillFails() throws Exception {
        AppVersionVo vo = manifest();
        vo.setFileName("app-1.0.0.apk");
        writeManifest(vo);

        assertThrows(CustomExceptions.class, () -> service.resolveByVersionChannel("lite"));
    }

    @Test
    @DisplayName("产物文件名为空：视为该渠道无产物，失败而非下发空键")
    void resolve_blankFileName_fails() throws Exception {
        AppVersionVo vo = manifest();
        Map<String, ChannelVersion> channels = new LinkedHashMap<>();
        channels.put("full", artifact("  ", 1L, "sha"));
        vo.setChannels(channels);
        writeManifest(vo);

        assertThrows(CustomExceptions.class, () -> service.resolveByVersionChannel("full"));
    }

    @Test
    @DisplayName("版本文件不存在：失败提示「暂无版本信息」")
    void resolve_missingFile_fails() {
        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.resolveByVersionChannel("full"));
        assertEquals("暂无版本信息", ex.getMessage());
    }

    @Test
    @DisplayName("resolveArtifact：下载接口按渠道取产物，不含对象前缀")
    void resolveArtifact_returnsRawFileName() throws Exception {
        AppVersionVo vo = manifest();
        Map<String, ChannelVersion> channels = new LinkedHashMap<>();
        channels.put("full", artifact("app-1.0.0.apk", 123L, "sha-full"));
        channels.put("lite", artifact("app-lite-1.0.0.apk", 456L, "sha-lite"));
        vo.setChannels(channels);
        writeManifest(vo);

        assertEquals("app-1.0.0.apk", service.resolveArtifact("full").getFileName());
        assertEquals("app-lite-1.0.0.apk", service.resolveArtifact("lite").getFileName());
        // 产物只带对象名，对象前缀由下载接口按渠道拼接，两层职责不串
        assertEquals(123L, service.resolveArtifact("full").getFileSize());
        assertEquals(456L, service.resolveArtifact("lite").getFileSize());
    }

    @Test
    @DisplayName("AppChannel.fromCode：合法值命中，空值/非法值返回 null")
    void appChannel_fromCode() {
        assertEquals(AppChannel.FULL, AppChannel.fromCode("full"));
        assertEquals(AppChannel.LITE, AppChannel.fromCode("LITE"));
        assertNull(AppChannel.fromCode(null));
        assertNull(AppChannel.fromCode(" "));
        assertNull(AppChannel.fromCode("ios"));
    }

    @Test
    @DisplayName("AppChannel.resolveOrDefault：缺省回落 full，非法取值返回 null（不静默给 full）")
    void appChannel_resolveOrDefault() {
        assertEquals(AppChannel.FULL, AppChannel.resolveOrDefault(null));
        assertEquals(AppChannel.FULL, AppChannel.resolveOrDefault(" "));
        assertEquals(AppChannel.FULL, AppChannel.resolveOrDefault("full"));
        assertEquals(AppChannel.LITE, AppChannel.resolveOrDefault("LITE"));
        assertNull(AppChannel.resolveOrDefault("beta"));
    }
}
