package com.smartwash.service.impl;

import com.smartwash.common.AppChannel;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.service.AppVersionService;
import com.smartwash.utils.JsonUtils;
import com.smartwash.vo.AppVersionVo;
import com.smartwash.vo.ChannelVersion;
import com.smartwash.vo.ChannelVersionVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 应用版本清单读取实现：现读现返磁盘 version.json，不做缓存。
 */
@Slf4j
@Service
public class AppVersionServiceImpl implements AppVersionService {

    /** 版本文件路径，可通过配置文件覆盖；默认 /app/config/releases/latest/version.json */
    @Value("${app.version.file-path:/app/config/releases/latest/version.json}")
    private String versionFilePath;

    @Override
    public AppVersionVo readManifest() {
        Path path = Paths.get(versionFilePath);
        if (!Files.exists(path)) {
            log.warn("版本文件不存在：{}", versionFilePath);
            throw new CustomExceptions("暂无版本信息");
        }
        try {
            String content = Files.readString(path);
            AppVersionVo vo = JsonUtils.parseObject(content, AppVersionVo.class);
            if (vo == null) {
                log.error("版本文件内容为空或解析结果为空：{}", versionFilePath);
                throw new CustomExceptions("版本信息读取失败");
            }
            log.debug("读取版本文件成功：versionCode={}, versionName={}, channels={}",
                    vo.getVersionCode(), vo.getVersionName(),
                    vo.getChannels() == null ? 0 : vo.getChannels().size());
            return vo;
        } catch (CustomExceptions e) {
            throw e;
        } catch (Exception e) {
            log.error("读取或解析版本文件失败：{}", versionFilePath, e);
            throw new CustomExceptions("版本信息读取失败");
        }
    }

    @Override
    public ChannelVersionVo resolveByVersionChannel(String channelCode) {
        AppVersionVo manifest = readManifest();
        AppChannel channel = resolveChannel(channelCode);
        ChannelVersion artifact = artifactOf(manifest, channel);

        ChannelVersionVo vo = new ChannelVersionVo();
        vo.setChannel(channel.getCode());
        vo.setVersionCode(manifest.getVersionCode());
        vo.setVersionName(manifest.getVersionName());
        vo.setApkUrl(manifest.getApkUrl());
        vo.setFileName(artifact.getFileName());
        vo.setFileSize(artifact.getFileSize());
        vo.setSha256(artifact.getSha256());
        vo.setForceUpdate(manifest.getForceUpdate());
        vo.setMinVersionCode(manifest.getMinVersionCode());
        vo.setChangelog(manifest.getChangelog());
        vo.setReleaseDate(manifest.getReleaseDate());
        return vo;
    }

    @Override
    public ChannelVersion resolveArtifact(String channelCode) {
        return artifactOf(readManifest(), resolveChannel(channelCode));
    }

    /**
     * 解析渠道：缺省（空）回落 full；非法取值直接失败，避免端上拼错渠道静默拿到 full 包。
     */
    private AppChannel resolveChannel(String channelCode) {
        AppChannel channel = AppChannel.resolveOrDefault(channelCode);
        if (channel == null) {
            throw new CustomExceptions("不支持的更新渠道：" + channelCode);
        }
        return channel;
    }

    /**
     * 从清单中取渠道产物。
     *
     * <p>full 渠道以顶层 {@code fileName/fileSize/sha256} 为事实源（任务约定「保留顶层 full 字段」，
     * 二者同值）；顶层缺失时回落 {@code channels.full}，兼容只写分组的清单。
     * lite 渠道只认 {@code channels.lite}。</p>
     */
    private ChannelVersion artifactOf(AppVersionVo manifest, AppChannel channel) {
        if (channel == AppChannel.FULL && manifest.getFileName() != null && !manifest.getFileName().isBlank()) {
            ChannelVersion artifact = new ChannelVersion();
            artifact.setFileName(manifest.getFileName());
            artifact.setFileSize(manifest.getFileSize());
            artifact.setSha256(manifest.getSha256());
            return artifact;
        }

        Map<String, ChannelVersion> channels = manifest.getChannels();
        ChannelVersion artifact = channels == null ? null : channels.get(channel.getCode());
        if (artifact == null || artifact.getFileName() == null || artifact.getFileName().isBlank()) {
            log.error("版本清单缺少渠道产物：channel={}, file={}", channel.getCode(), versionFilePath);
            throw new CustomExceptions("暂无 " + channel.getDescription() + " 的版本信息");
        }
        return artifact;
    }
}
