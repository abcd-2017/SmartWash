package com.smartwash.service;

import com.smartwash.common.AppChannel;
import com.smartwash.vo.AppVersionVo;
import com.smartwash.vo.ChannelVersion;
import com.smartwash.vo.ChannelVersionVo;

/**
 * 应用版本清单读取服务。
 *
 * <p>版本信息现读现返服务器磁盘的 version.json（发版后立即生效，无缓存），
 * 并按 channel（full / lite）下发各自的 APK 产物，避免两个 App 互推错包。</p>
 */
public interface AppVersionService {

    /**
     * 读取原始版本清单（含 channels 分组与顶层兼容字段）。
     *
     * @return 版本清单 VO
     * @throws com.smartwash.exception.CustomExceptions 文件不存在或解析失败
     */
    AppVersionVo readManifest();

    /**
     * 按渠道取版本信息（扁平结构，端上沿用既有字段名）。
     *
     * <p>channel 为空时按 {@link AppChannel#FULL} 处理；渠道在清单中不存在时抛业务异常。</p>
     *
     * @param channelCode 渠道标识（可空，缺省 full）
     * @return 扁平化版本信息
     */
    ChannelVersionVo resolveByVersionChannel(String channelCode);

    /**
     * 按渠道取 APK 产物信息（供下载接口拼接 MinIO 对象键）。
     *
     * @param channelCode 渠道标识（可空，缺省 full）
     * @return 该渠道的产物信息
     */
    ChannelVersion resolveArtifact(String channelCode);
}
