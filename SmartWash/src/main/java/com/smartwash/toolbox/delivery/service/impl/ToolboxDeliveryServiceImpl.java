package com.smartwash.toolbox.delivery.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.toolbox.delivery.entity.ToolboxDeliveryItem;
import com.smartwash.toolbox.delivery.from.CreateDeliverySessionFrom;
import com.smartwash.toolbox.delivery.from.PublishDeliveryTextFrom;
import com.smartwash.toolbox.delivery.mapper.ToolboxDeliveryItemMapper;
import com.smartwash.toolbox.mapper.ToolboxShortCodeMapper;
import com.smartwash.toolbox.delivery.service.ToolboxDeliveryService;
import com.smartwash.toolbox.delivery.toolkit.DeliveryObjectStorage;
import com.smartwash.toolbox.delivery.vo.DeliveryItemTakenVo;
import com.smartwash.toolbox.delivery.vo.DeliveryItemVo;
import com.smartwash.toolbox.delivery.vo.DeliverySessionDetailVo;
import com.smartwash.toolbox.delivery.vo.DeliverySessionVo;
import com.smartwash.toolbox.entity.ToolboxShortCode;
import com.smartwash.toolbox.service.ToolboxShortCodeService;
import com.smartwash.toolbox.toolkit.ShortCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 设备投递服务实现。
 * 并发与安全语义（调研 §4.3/§4.5）：
 * - 码即凭证：随机码由 uk_code 唯一索引裁决，冲突重抽，禁止先查后插；
 * - 取走即焚：原子认领（UPDATE ... WHERE taken=0 判影响行数），文件流正常完成才删对象，
 *   中断释放认领允许重取，删除失败保 taken=1（fail-closed）；
 * - 读路径即时失效：进入/发布/列表/取走/内容全部先判 expire_at，过期或已关闭一律 404，
 *   不使用负缓存（投递失效粒度是分钟级，负缓存会吃掉焚毁时效）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolboxDeliveryServiceImpl extends ServiceImpl<ToolboxDeliveryItemMapper, ToolboxDeliveryItem>
        implements ToolboxDeliveryService {

    /** 会话主表 content_type：6 设备投递 */
    private static final int CONTENT_TYPE_DELIVERY = 6;
    /** 可见性固定 1 公开：凭码即可进入（匿名对端语义） */
    private static final int PUBLIC_TRUE = 1;
    /** 方向：1手机→电脑 2电脑→手机 */
    private static final int DIRECTION_OWNER_TO_PEER = 1;
    private static final int DIRECTION_PEER_TO_OWNER = 2;
    /** 条目类型：1文本 2图片 3文件 */
    private static final int KIND_TEXT = 1;
    private static final int KIND_IMAGE = 2;
    private static final int KIND_FILE = 3;
    /** 随机码冲突重试上限（54^7 ≈ 1.3 万亿空间，10 次数学上几乎不可达） */
    private static final int MAX_RETRY = 10;
    /** 会话默认有效期（分钟）与上限 */
    private static final int DEFAULT_DURATION_MINUTES = 30;
    /** 写路径上限：条目数 / 单文件字节 / 会话总字节（超限 413，对齐 PsiTransfer maxBucketSize） */
    private static final long MAX_ITEMS = 50;
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    private static final long MAX_SESSION_BYTES = 200L * 1024 * 1024;
    /** 文本摘要长度：title 缺省时截取正文前 30 字符 */
    private static final int TEXT_SUMMARY_LENGTH = 30;
    /** 进入失败防爆破延迟（对齐 PsiTransfer bcrypt 失败延迟 500ms） */
    private static final long BRUTE_FORCE_DELAY_MS = 500;
    /** 对端占用键前缀：一个会话同一时刻只允许一个对端在场（对齐 LocalSend 单活动会话 409） */
    private static final String OCCUPY_KEY_PREFIX = "smartwash:toolbox:delivery:occupy:";
    /** 业务码语义：HTTP 对齐（{code,message,data} 信封） */
    private static final int CODE_NOT_FOUND = 404;
    private static final int CODE_FORBIDDEN = 403;
    private static final int CODE_OCCUPIED = 409;
    private static final int CODE_PAYLOAD_TOO_LARGE = 413;

    private final ShortCodeGenerator shortCodeGenerator;
    private final ToolboxShortCodeService toolboxShortCodeService;
    private final ToolboxShortCodeMapper toolboxShortCodeMapper;
    private final DeliveryObjectStorage deliveryObjectStorage;
    private final StringRedisTemplate stringRedisTemplate;
    private final PasswordEncoder passwordEncoder;

    /** 会话对端 H5 页地址的域名前缀（拼 {base}/d/{code}），生产通过环境变量 TOOLBOX_PUBLIC_BASE_URL 注入 */
    @Value("${toolbox.public-base-url}")
    private String publicBaseUrl;

    // ==================== 会话（owner 侧） ====================

    @Override
    public DeliverySessionVo createSession(CreateDeliverySessionFrom from, Long ownerUserId) {
        int duration = from.getDurationMinutes() == null ? DEFAULT_DURATION_MINUTES : from.getDurationMinutes();
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(duration);
        String passwordHash = StrUtil.isBlank(from.getPassword()) ? null : passwordEncoder.encode(from.getPassword());

        // 随机码由唯一索引最终裁决，冲突重抽（禁止先查后插，存在 TOCTOU 竞态窗口）
        String code = shortCodeGenerator.randomCode();
        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                ToolboxShortCode session = new ToolboxShortCode()
                        .setCode(code)
                        .setContentType(CONTENT_TYPE_DELIVERY)
                        .setOwnerUserId(ownerUserId)
                        .setIsPublic(PUBLIC_TRUE)
                        .setPasswordHash(passwordHash)
                        .setBurnAfterRead(0)
                        .setExpireAt(expireAt);
                // 单条 insert 自带原子性，不套外层事务
                toolboxShortCodeService.save(session);
                log.info("投递会话创建成功, code: {}, ownerUserId: {}, 有效期: {} 分钟", code, ownerUserId, duration);
                return buildSessionVo(session, countItems(code));
            } catch (DuplicateKeyException e) {
                log.debug("投递码冲突重试, 第 {} 次", i + 1);
                code = shortCodeGenerator.randomCode();
            }
        }
        throw new CustomExceptions("投递码生成繁忙，请重试");
    }

    @Override
    public DeliverySessionDetailVo ownerSessionDetail(String code, Long ownerUserId) {
        ToolboxShortCode session = getOwnedSession(code, ownerUserId);
        return buildDetailVo(session);
    }

    @Override
    public void closeSession(String code, Long ownerUserId) {
        ToolboxShortCode session = getOwnedSession(code, ownerUserId);
        // 条件 UPDATE 判影响行数（状态操作硬性约束），expire_at 前置即读路径立即失效
        boolean updated = toolboxShortCodeService.update(new UpdateWrapper<ToolboxShortCode>()
                .eq("id", session.getId())
                .eq("content_type", CONTENT_TYPE_DELIVERY)
                .setSql("expire_at = NOW()"));
        if (!updated) {
            throw new CustomExceptions(CODE_NOT_FOUND, "投递会话不存在或已失效");
        }
        // 关闭即解除对端占用，占用键跟随会话生命周期
        stringRedisTemplate.delete(OCCUPY_KEY_PREFIX + code);
        log.info("投递会话已关闭, code: {}, ownerUserId: {}", code, ownerUserId);
    }

    // ==================== 会话（对端侧，凭码匿名） ====================

    @Override
    public DeliverySessionVo enter(String code, String password) {
        ToolboxShortCode session = getActiveSession(code);
        // max_visits 复用为对端进入次数上限（对齐 Pingvin Share visitor limits），达上限按 404 语义
        if (session.getMaxVisits() != null
                && session.getClickCount() != null
                && session.getClickCount() >= session.getMaxVisits()) {
            throw new CustomExceptions(CODE_NOT_FOUND, "投递会话不存在或已失效");
        }
        // 可选密码：BCrypt 校验（复用内核类型3交付箱语义），失败故意延迟防暴力破解
        if (StrUtil.isNotBlank(session.getPasswordHash())) {
            if (!passwordEncoder.matches(password == null ? "" : password, session.getPasswordHash())) {
                delayAgainstBruteForce();
                throw new CustomExceptions(CODE_FORBIDDEN, "进入密码错误");
            }
        }
        // 单对端占用：setIfAbsent 原子写（SETEX 语义，TTL=会话剩余时长），被占 409
        Duration remain = Duration.between(LocalDateTime.now(), session.getExpireAt());
        Boolean occupied = stringRedisTemplate.opsForValue()
                .setIfAbsent(OCCUPY_KEY_PREFIX + code, "1", remain.isNegative() ? Duration.ZERO : remain);
        if (!Boolean.TRUE.equals(occupied)) {
            throw new CustomExceptions(CODE_OCCUPIED, "会话已被其他设备占用");
        }
        // 进入成功才计数：主表原子自增，防并发丢失更新
        toolboxShortCodeService.update(new UpdateWrapper<ToolboxShortCode>()
                .eq("code", code)
                .setSql("click_count = click_count + 1"));
        log.debug("对端进入投递会话, code: {}", code);
        return buildSessionVo(session, countItems(code));
    }

    @Override
    public DeliverySessionDetailVo peerSessionDetail(String code) {
        return buildDetailVo(getActiveSession(code));
    }

    @Override
    public DeliveryItemVo peerPublishText(String code, PublishDeliveryTextFrom from) {
        return publishTextItem(getActiveSession(code), DIRECTION_PEER_TO_OWNER, from);
    }

    @Override
    public DeliveryItemVo peerPublishFile(String code, Integer kind, MultipartFile file) {
        return publishFileItem(getActiveSession(code), DIRECTION_PEER_TO_OWNER, kind, file);
    }

    // ==================== 条目发布（owner 侧） ====================

    @Override
    public DeliveryItemVo publishText(String code, PublishDeliveryTextFrom from, Long ownerUserId) {
        return publishTextItem(getOwnedSession(code, ownerUserId), DIRECTION_OWNER_TO_PEER, from);
    }

    @Override
    public DeliveryItemVo publishFile(String code, Integer kind, MultipartFile file, Long ownerUserId) {
        return publishFileItem(getOwnedSession(code, ownerUserId), DIRECTION_OWNER_TO_PEER, kind, file);
    }

    // ==================== 条目取走与内容（双端同构，码即凭证） ====================

    @Override
    public DeliveryItemTakenVo takeItem(String code, Long itemId) {
        getActiveSession(code);
        ToolboxDeliveryItem item = getItem(itemId, code);
        // 原子认领：仅当未取走时置位，判影响行数放行（防并发双读，PsiTransfer claim 语义）
        if (baseMapper.claim(itemId) != 1) {
            if (item.getTaken() != null && item.getTaken() == 1) {
                throw new CustomExceptions(CODE_OCCUPIED, "条目已被取走");
            }
            throw new CustomExceptions(CODE_NOT_FOUND, "投递条目不存在或已失效");
        }
        if (Integer.valueOf(KIND_TEXT).equals(item.getKind())) {
            // 文本：认领成功后立即焚毁（置 NULL 须 setSql，全局 update-strategy=not_empty 会忽略 null 字段），
            // 内容仅本次响应返回一次
            this.update(new UpdateWrapper<ToolboxDeliveryItem>()
                    .eq("id", itemId)
                    .setSql("text_content = NULL"));
            log.debug("文本条目已认领焚毁, itemId: {}, code: {}", itemId, code);
            return new DeliveryItemTakenVo()
                    .setId(itemId)
                    .setKind(item.getKind())
                    .setTitle(item.getTitle())
                    .setTextContent(item.getTextContent())
                    .setContentUrl(null);
        }
        // 文件：认领后经 content 接口流式下载（下载完成才删对象，中断释放认领）
        return new DeliveryItemTakenVo()
                .setId(itemId)
                .setKind(item.getKind())
                .setTitle(item.getTitle())
                .setTextContent(null)
                .setContentUrl("/web/delivery/" + code + "/items/" + itemId + "/content");
    }

    @Override
    public DeliveryContent content(String code, Long itemId) {
        getActiveSession(code);
        ToolboxDeliveryItem item = getItem(itemId, code);
        if (Integer.valueOf(KIND_TEXT).equals(item.getKind())) {
            // 文本：正文可能已焚毁（null，占位状态照常返回）
            return new DeliveryContent(item.getKind(), item.getTextContent(), null);
        }
        if (StrUtil.isBlank(item.getObjectKey())) {
            throw new CustomExceptions(CODE_NOT_FOUND, "投递条目不存在或已失效");
        }
        // 文件：须先 take 认领再下载，未认领直接拉流 409（认领在读之前，防并发双读）
        if (item.getTaken() == null || item.getTaken() != 1) {
            throw new CustomExceptions(CODE_OCCUPIED, "条目尚未取走，请先调用取走接口");
        }
        // 开流与生命周期回调由控制器的流式响应阶段执行（openStream / onComplete / onInterrupted）
        DeliveryFileDownload download = new DeliveryFileDownload(itemId, code, item.getObjectKey(),
                item.getMimeType(), item.getObjectSize(), item.getTitle());
        return new DeliveryContent(item.getKind(), null, download);
    }

    @Override
    public void onFileDownloadComplete(DeliveryFileDownload download) {
        try {
            deliveryObjectStorage.remove(download.getObjectKey());
            log.debug("投递文件焚毁完成, itemId: {}, objectKey: {}", download.getItemId(), download.getObjectKey());
        } catch (Exception e) {
            // 删除失败保 taken=1（fail-closed）：宁可残余对象由清理扫描兜底，也不允许二次下载
            log.error("投递对象删除失败，保留认领待清理兜底, itemId: {}, objectKey: {}",
                    download.getItemId(), download.getObjectKey(), e);
        }
    }

    @Override
    public void onFileDownloadInterrupted(DeliveryFileDownload download) {
        // 中断释放认领，允许重取（对齐 PsiTransfer res.on('close') + !writableFinished 释放语义）
        baseMapper.release(download.getItemId());
        log.debug("投递文件下载中断，已释放认领, itemId: {}", download.getItemId());
    }

    // ==================== 物理回收（cleaner 调用） ====================

    @Override
    public void purgeExpiredSessions(List<ToolboxShortCode> expiredSessions) {
        for (ToolboxShortCode session : expiredSessions) {
            // 次序：先删 MinIO 前缀对象（防孤儿对象）→ 删条目 → 删会话（对齐现有 cleaner）
            deliveryObjectStorage.removeByPrefix("delivery/" + session.getCode() + "/");
            this.remove(new LambdaQueryWrapper<ToolboxDeliveryItem>()
                    .eq(ToolboxDeliveryItem::getSessionCode, session.getCode()));
            toolboxShortCodeService.removeById(session.getId());
            stringRedisTemplate.delete(OCCUPY_KEY_PREFIX + session.getCode());
        }
        log.info("过期投递会话清理完成, 共 {} 个会话", expiredSessions.size());
    }

    // ==================== 内部方法 ====================

    private DeliveryItemVo publishTextItem(ToolboxShortCode session, int direction, PublishDeliveryTextFrom from) {
        checkItemQuota(session.getCode());
        String title = StrUtil.blankToDefault(from.getTitle(),
                StrUtil.subPre(from.getTextContent().trim(), TEXT_SUMMARY_LENGTH));
        ToolboxDeliveryItem item = new ToolboxDeliveryItem()
                .setSessionCode(session.getCode())
                .setDirection(direction)
                .setKind(KIND_TEXT)
                .setTitle(title)
                .setTextContent(from.getTextContent())
                .setBurnAfterTake(1)
                .setTaken(0);
        this.save(item);
        return buildItemVo(item);
    }

    private DeliveryItemVo publishFileItem(ToolboxShortCode session, int direction, Integer kind, MultipartFile file) {
        if (kind == null || (kind != KIND_IMAGE && kind != KIND_FILE)) {
            throw new CustomExceptions("文件条目类型须为2图片或3文件");
        }
        if (file == null || file.isEmpty()) {
            throw new CustomExceptions("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new CustomExceptions(CODE_PAYLOAD_TOO_LARGE, "单个文件不能超过20MB");
        }
        checkItemQuota(session.getCode());
        // 会话总字节上限：现有累计 + 本次 > 200MB 拒收（413，对齐 PsiTransfer maxBucketSize）
        long existedBytes = baseMapper.sumObjectSize(session.getCode());
        if (existedBytes + file.getSize() > MAX_SESSION_BYTES) {
            throw new CustomExceptions(CODE_PAYLOAD_TOO_LARGE, "会话总容量不能超过200MB");
        }

        String objectKey = null;
        try {
            // sha256 完整性校验值随上传落库（LocalSend 协议同名字段，取走端可选校验）
            String sha256;
            try (InputStream is = file.getInputStream()) {
                sha256 = DigestUtil.sha256Hex(is);
            }
            objectKey = deliveryObjectStorage.put(file, session.getCode());
            ToolboxDeliveryItem item = new ToolboxDeliveryItem()
                    .setSessionCode(session.getCode())
                    .setDirection(direction)
                    .setKind(kind)
                    .setTitle(StrUtil.subPre(file.getOriginalFilename(), 255))
                    .setObjectKey(objectKey)
                    .setObjectSize(file.getSize())
                    .setMimeType(StrUtil.subPre(file.getContentType(), 128))
                    .setSha256(sha256)
                    .setBurnAfterTake(1)
                    .setTaken(0);
            this.save(item);
            return buildItemVo(item);
        } catch (CustomExceptions e) {
            throw e;
        } catch (Exception e) {
            // 上传后任一步失败：补偿删除已传对象，不留孤儿；补偿失败仅记录，由清理扫描按前缀兜底
            if (objectKey != null) {
                try {
                    deliveryObjectStorage.remove(objectKey);
                } catch (Exception cleanupError) {
                    log.error("投递条目落库失败，补偿删除对象失败, objectKey: {}", objectKey, cleanupError);
                }
            }
            log.error("投递条目落库失败, sessionCode: {}", session.getCode(), e);
            throw new CustomExceptions("文件上传失败，请重试");
        }
    }

    /** 条目数上限校验（413） */
    private void checkItemQuota(String sessionCode) {
        long count = this.count(new LambdaQueryWrapper<ToolboxDeliveryItem>()
                .eq(ToolboxDeliveryItem::getSessionCode, sessionCode));
        if (count >= MAX_ITEMS) {
            throw new CustomExceptions(CODE_PAYLOAD_TOO_LARGE, "会话条目数已达上限（50条）");
        }
    }

    /** owner 会话读取：归属不符按 404 语义，不暴露存在性 */
    private ToolboxShortCode getOwnedSession(String code, Long ownerUserId) {
        ToolboxShortCode session = getSessionByCode(code);
        if (!ownerUserId.equals(session.getOwnerUserId())) {
            log.debug("投递会话归属校验不符, code: {}, ownerUserId: {}", code, ownerUserId);
            throw new CustomExceptions(CODE_NOT_FOUND, "投递会话不存在或已失效");
        }
        return session;
    }

    /** 对端会话读取：凭码即凭证，仅校验存在与生命周期 */
    private ToolboxShortCode getActiveSession(String code) {
        return getSessionByCode(code);
    }

    /** 会话读取与读路径失效判断：不存在/非投递类型/已过期或已关闭一律 404 语义（不用负缓存） */
    private ToolboxShortCode getSessionByCode(String code) {
        ToolboxShortCode session = toolboxShortCodeMapper.selectOne(
                new LambdaQueryWrapper<ToolboxShortCode>().eq(ToolboxShortCode::getCode, code));
        if (session == null || !Integer.valueOf(CONTENT_TYPE_DELIVERY).equals(session.getContentType())) {
            throw new CustomExceptions(CODE_NOT_FOUND, "投递会话不存在或已失效");
        }
        if (session.getExpireAt() != null && session.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new CustomExceptions(CODE_NOT_FOUND, "投递会话不存在或已失效");
        }
        return session;
    }

    /** 条目读取：存在且归属当前会话，否则 404（防跨会话枚举条目 id） */
    private ToolboxDeliveryItem getItem(Long itemId, String sessionCode) {
        ToolboxDeliveryItem item = this.getById(itemId);
        if (item == null || !sessionCode.equals(item.getSessionCode())) {
            throw new CustomExceptions(CODE_NOT_FOUND, "投递条目不存在或已失效");
        }
        return item;
    }

    private void delayAgainstBruteForce() {
        try {
            Thread.sleep(BRUTE_FORCE_DELAY_MS);
        } catch (InterruptedException ie) {
            // 线程关闭信号，恢复中断标志即可，debug 留痕
            log.debug("防爆破延迟等待被中断，线程恢复中断标志");
            Thread.currentThread().interrupt();
        }
    }

    private long countItems(String sessionCode) {
        return this.count(new LambdaQueryWrapper<ToolboxDeliveryItem>()
                .eq(ToolboxDeliveryItem::getSessionCode, sessionCode));
    }

    private long countPending(String sessionCode) {
        return this.count(new LambdaQueryWrapper<ToolboxDeliveryItem>()
                .eq(ToolboxDeliveryItem::getSessionCode, sessionCode)
                .eq(ToolboxDeliveryItem::getTaken, 0));
    }

    private DeliverySessionDetailVo buildDetailVo(ToolboxShortCode session) {
        List<ToolboxDeliveryItem> items = this.list(new LambdaQueryWrapper<ToolboxDeliveryItem>()
                .eq(ToolboxDeliveryItem::getSessionCode, session.getCode())
                .orderByAsc(ToolboxDeliveryItem::getCreatedAt));
        return new DeliverySessionDetailVo()
                .setSession(buildSessionVo(session, (long) items.size()))
                .setItems(items.stream().map(this::buildItemVo).collect(Collectors.toList()));
    }

    private DeliverySessionVo buildSessionVo(ToolboxShortCode session, Long itemCount) {
        String base = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return new DeliverySessionVo()
                .setCode(session.getCode())
                .setExpireAt(session.getExpireAt())
                .setClickCount(session.getClickCount())
                .setHasPassword(StrUtil.isNotBlank(session.getPasswordHash()))
                .setItemCount(itemCount)
                .setPendingCount(countPending(session.getCode()))
                .setEnterUrl(base + "/d/" + session.getCode())
                .setCreatedAt(session.getCreatedAt());
    }

    /** 条目 Vo：hasContent 对文本=正文仍在，对文件=未认领（焚毁即占位，不暴露内容态细节） */
    private DeliveryItemVo buildItemVo(ToolboxDeliveryItem item) {
        boolean hasContent = Integer.valueOf(KIND_TEXT).equals(item.getKind())
                ? item.getTextContent() != null
                : item.getTaken() == null || item.getTaken() == 0;
        return new DeliveryItemVo()
                .setId(item.getId())
                .setDirection(item.getDirection())
                .setKind(item.getKind())
                .setTitle(item.getTitle())
                .setTaken(item.getTaken())
                .setTakenAt(item.getTakenAt())
                .setObjectSize(item.getObjectSize())
                .setMimeType(item.getMimeType())
                .setSha256(item.getSha256())
                .setHasContent(hasContent)
                .setCreatedAt(item.getCreatedAt());
    }
}
