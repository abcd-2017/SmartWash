package com.smartwash.toolbox.delivery.service;

import com.smartwash.toolbox.delivery.entity.ToolboxDeliveryItem;
import com.smartwash.toolbox.delivery.from.CreateDeliverySessionFrom;
import com.smartwash.toolbox.delivery.from.PublishDeliveryTextFrom;
import com.smartwash.toolbox.delivery.vo.DeliveryItemTakenVo;
import com.smartwash.toolbox.delivery.vo.DeliveryItemVo;
import com.smartwash.toolbox.delivery.vo.DeliverySessionDetailVo;
import com.smartwash.toolbox.delivery.vo.DeliverySessionVo;
import com.smartwash.toolbox.entity.ToolboxShortCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 设备投递服务。
 * 会话主记录复用 toolbox_short_code（content_type=6），本服务只负责投递语义：
 * 原子认领取走即焚、读路径即时失效（不用负缓存）、对端占用与写路径上限。
 */
public interface ToolboxDeliveryService {

    /** 创建投递会话（owner，ROLE_USER）：随机码 + uk_code 冲突重试，默认 30 分钟有效期 */
    DeliverySessionVo createSession(CreateDeliverySessionFrom from, Long ownerUserId);

    /** owner 会话详情 + 全量条目（含已焚毁占位），校验归属，不符按 404 语义 */
    DeliverySessionDetailVo ownerSessionDetail(String code, Long ownerUserId);

    /** 关闭会话（owner）：expire_at 前置为当前时刻（条件 UPDATE 判影响行数），读路径立即失效 */
    void closeSession(String code, Long ownerUserId);

    /** 发布文本条目（owner direction=1）：条目数上限 413 */
    DeliveryItemVo publishText(String code, PublishDeliveryTextFrom from, Long ownerUserId);

    /** 发布文件/图片条目（owner direction=1）：条目数/单文件/会话总字节上限 413 */
    DeliveryItemVo publishFile(String code, Integer kind, MultipartFile file, Long ownerUserId);

    /** 对端进入：可选密码（失败延迟 500ms 防爆破）、单对端占用 409、进入次数上限 404、click_count 原子自增 */
    DeliverySessionVo enter(String code, String password);

    /** 对端条目列表（凭码匿名，读路径判过期） */
    DeliverySessionDetailVo peerSessionDetail(String code);

    /** 对端发布文本条目（direction=2） */
    DeliveryItemVo peerPublishText(String code, PublishDeliveryTextFrom from);

    /** 对端发布文件/图片条目（direction=2） */
    DeliveryItemVo peerPublishFile(String code, Integer kind, MultipartFile file);

    /** 取走条目（原子认领 UPDATE ... WHERE taken=0 判影响行数）：文本一步焚毁并返回内容；文件仅认领并返回下载路径 */
    DeliveryItemTakenVo takeItem(String code, Long itemId);

    /** 条目内容：kind=1 返回文本（焚毁后为 null）；kind=2/3 返回文件下载载荷（须先 take 认领，流式转发由控制器执行） */
    DeliveryContent content(String code, Long itemId);

    /** 文件流正常完成回调：删除对象；删除失败仅记录日志并保 taken=1（fail-closed，不允许二次下载） */
    void onFileDownloadComplete(DeliveryFileDownload download);

    /** 文件流中断回调：释放认领（taken 回 0），允许重取 */
    void onFileDownloadInterrupted(DeliveryFileDownload download);

    /** 物理回收（cleaner 调用）：先删 MinIO 前缀对象 → 删条目 → 删会话（防孤儿对象） */
    void purgeExpiredSessions(List<ToolboxShortCode> expiredSessions);

    /** 条目内容载荷：文本与文件二选一 */
    @Getter
    @AllArgsConstructor
    class DeliveryContent {

        /** 1文本 2图片 3文件 */
        private final Integer kind;

        /** kind=1：文本正文（焚毁后 null） */
        private final String textContent;

        /** kind=2/3：文件下载载荷 */
        private final DeliveryFileDownload fileDownload;
    }

    /** 文件下载载荷：流与生命周期回调所需的最小信息集 */
    @Getter
    @AllArgsConstructor
    class DeliveryFileDownload {

        private final Long itemId;

        private final String sessionCode;

        private final String objectKey;

        private final String mimeType;

        private final Long objectSize;

        private final String fileName;
    }
}
