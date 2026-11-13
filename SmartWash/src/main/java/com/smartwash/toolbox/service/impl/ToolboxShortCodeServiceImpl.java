package com.smartwash.toolbox.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.common.crypto.ToolboxGcmCipher;
import com.smartwash.entity.Users;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.mapper.UsersMapper;
import com.smartwash.toolbox.entity.ToolboxShortCode;
import com.smartwash.toolbox.entity.ToolboxShortVisit;
import com.smartwash.toolbox.from.AddShortCodeFrom;
import com.smartwash.toolbox.from.SearchShortCodeFrom;
import com.smartwash.toolbox.from.UpdateShortCodeFrom;
import com.smartwash.toolbox.mapper.ToolboxShortCodeMapper;
import com.smartwash.toolbox.service.ToolboxShortCodeService;
import com.smartwash.toolbox.service.ToolboxShortVisitService;
import com.smartwash.toolbox.toolkit.PublicBaseUrlResolver;
import com.smartwash.toolbox.toolkit.SecretState;
import com.smartwash.toolbox.toolkit.ShortCodeContentType;
import com.smartwash.toolbox.toolkit.ShortCodeGenerator;
import com.smartwash.toolbox.toolkit.WebBaseUrlResolver;
import com.smartwash.toolbox.vo.AdminShortCodeVo;
import com.smartwash.toolbox.vo.SecretMetaVo;
import com.smartwash.toolbox.vo.SecretViewVo;
import com.smartwash.toolbox.vo.ShortCodeStatsVo;
import com.smartwash.toolbox.vo.ShortCodeVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 短码内核服务实现。
 * 读路径：负缓存(60s) → 跳转缓存(10min，仅「干净」记录) → DB → 生命周期判断；
 * 缓存失效联动：更新/删除/创建时 DEL 对应 key，保证 302 语义下改向即时生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolboxShortCodeServiceImpl extends ServiceImpl<ToolboxShortCodeMapper, ToolboxShortCode>
        implements ToolboxShortCodeService {

    /** 随机码冲突重试上限（54^7 ≈ 1.3 万亿空间，10 次数学上几乎不可达） */
    private static final int MAX_RETRY = 10;
    /** 跳转缓存 TTL：仅缓存「干净」记录（公开 + 类型1/2 + 无生命周期字段） */
    private static final long REDIRECT_TTL_MINUTES = 10;
    /** 空值负缓存 TTL：拦截不存在的短码穿透 */
    private static final long MISS_TTL_SECONDS = 60;
    /** 可见性：1公开 2私有 */
    private static final int PUBLIC_TRUE = 1;
    private static final int PUBLIC_FALSE = 2;
    /** 私密文本密钥版本（TOOLBOX_MASTER_KEY 当前代） */
    private static final int SECRET_KEY_VERSION = 1;
    /** 私密文本正文上限（与 AddShortCodeFrom @Size 一致，服务端二次校验） */
    private static final int MAX_SECRET_CHARS = 2000;
    /** 揭示失败防爆破延迟（对齐投递模块 BCrypt 失败延迟 500ms） */
    private static final long BRUTE_FORCE_DELAY_MS = 500;
    /** 业务码语义（{code,message,data} 信封，对齐投递模块）：与 HTTP 状态语义一致 */
    private static final int CODE_NOT_FOUND = 404;
    private static final int CODE_FORBIDDEN = 403;
    private static final int CODE_GONE = 410;
    private static final int CODE_CONFLICT = 409;

    private static final String REDIRECT_KEY_PREFIX = "smartwash:toolbox:redirect:";
    private static final String MISS_KEY_PREFIX = "smartwash:toolbox:miss:";

    private final ShortCodeGenerator shortCodeGenerator;
    private final ToolboxShortVisitService toolboxShortVisitService;
    private final StringRedisTemplate stringRedisTemplate;
    private final UsersMapper usersMapper;

    /** 短链对外域名解析：显式配置优先，未配置时按当前请求推导（见 PublicBaseUrlResolver） */
    private final PublicBaseUrlResolver publicBaseUrlResolver;

    /** Web 对外地址解析（私密文本分享链接来源；与短链域名不同源，只认显式配置） */
    private final WebBaseUrlResolver webBaseUrlResolver;

    /** 私密文本内容加密（TOOLBOX_MASTER_KEY，与占卜域分钥） */
    private final ToolboxGcmCipher toolboxGcmCipher;

    /** 口令 BCrypt（对齐投递模块与登录体系） */
    private final PasswordEncoder passwordEncoder;

    // ==================== 创建（唯一索引裁决 + 冲突重试） ====================

    @Override
    public ShortCodeVo createShortCode(AddShortCodeFrom from, Long ownerUserId) {
        String customCode = from.getCustomCode();
        if (customCode != null && !shortCodeGenerator.validCustomCode(customCode)) {
            throw new CustomExceptions("自定义短码须为4-16位字母数字或-_");
        }

        int contentType = from.getContentType() == null
                ? ShortCodeContentType.SHORT_LINK : from.getContentType();
        String target = null;
        String contentCipher = null;
        String passwordHash = null;
        int burnAfterRead = 0;
        int isPublic;
        LocalDateTime expireAt = from.getExpireAt();
        switch (contentType) {
            case ShortCodeContentType.SHORT_LINK -> {
                target = validateTarget(from.getTarget());
                isPublic = normalizeVisibility(from.getIsPublic());
            }
            case ShortCodeContentType.SECRET_TEXT -> {
                // 私密文本：正文必填并加密落库、可选口令 BCrypt、恒公开（接收者匿名查看）、必须设置未来过期时间
                contentCipher = validateAndEncryptSecret(from.getContent());
                passwordHash = StrUtil.isBlank(from.getPassword())
                        ? null : passwordEncoder.encode(from.getPassword());
                burnAfterRead = 1;
                isPublic = PUBLIC_TRUE;
                if (expireAt == null || !expireAt.isAfter(LocalDateTime.now())) {
                    throw new CustomExceptions("私密文本必须设置晚于当前时刻的过期时间");
                }
            }
            default -> throw new CustomExceptions("不支持的内容类型");
        }

        // 别名直接使用，随机码冲突时由唯一索引裁决后重生成（禁止先查后插，存在 TOCTOU 竞态窗口）
        String code = StrUtil.blankToDefault(customCode, shortCodeGenerator.randomCode());
        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                ToolboxShortCode entity = new ToolboxShortCode()
                        .setCode(code)
                        .setContentType(contentType)
                        .setTarget(target)
                        .setContent(contentCipher)
                        .setPasswordHash(passwordHash)
                        .setBurnAfterRead(burnAfterRead)
                        .setOwnerUserId(ownerUserId)
                        .setIsPublic(isPublic)
                        .setExpireAt(expireAt);
                // 单条 insert 自带原子性，不套外层事务
                this.save(entity);
                // 清空可能残留的负缓存，防止刚创建的码在 60s 内被误判 404
                stringRedisTemplate.delete(MISS_KEY_PREFIX + code);
                log.info("短码创建成功, code: {}, ownerUserId: {}, contentType: {}", code, ownerUserId, contentType);
                return buildVo(entity);
            } catch (DuplicateKeyException e) {
                if (customCode != null) {
                    // 别名冲突不重试，直接提示
                    throw new CustomExceptions("该短码已被占用");
                }
                log.debug("随机码冲突重试, 第 {} 次", i + 1);
                code = shortCodeGenerator.randomCode();
            }
        }
        throw new CustomExceptions("短码生成繁忙，请重试");
    }

    // ==================== 用户端管理 ====================

    @Override
    public IPage<ShortCodeVo> listMyShortCodes(SearchShortCodeFrom from, Long ownerUserId) {
        Page<ToolboxShortCode> result = this.page(new Page<>(from.getPage(), from.getSize()),
                ownerWrapper(from, ownerUserId));
        Page<ShortCodeVo> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::buildVo).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public ShortCodeVo updateShortCode(Long id, UpdateShortCodeFrom from, Long ownerUserId) {
        ToolboxShortCode record = getOwnedRecord(id, ownerUserId);
        if (Integer.valueOf(ShortCodeContentType.SECRET_TEXT).equals(record.getContentType())) {
            // 已焚毁/已过期一律终态，禁止续期；未焚毁仅允许改过期时间
            if (record.getBurnedAt() != null || isExpired(record)) {
                throw new CustomExceptions(CODE_CONFLICT, "已焚毁或已过期的私密文本不可更新");
            }
            if (from.getTarget() != null) {
                throw new CustomExceptions("私密文本不支持修改目标链接");
            }
        }
        if (from.getTarget() != null) {
            record.setTarget(validateTarget(from.getTarget()));
        }
        if (from.getExpireAt() != null) {
            // 传值即修改：必须晚于当前时刻，避免把短码改成「立即过期」的既成事实
            if (!from.getExpireAt().isAfter(LocalDateTime.now())) {
                throw new CustomExceptions("过期时间必须为未来时间");
            }
            record.setExpireAt(from.getExpireAt());
        }
        this.updateById(record);
        evictCache(record.getCode());
        log.info("短码已更新, code: {}, ownerUserId: {}", record.getCode(), ownerUserId);
        return buildVo(record);
    }

    @Override
    public void deleteShortCode(Long id, Long ownerUserId) {
        ToolboxShortCode record = getOwnedRecord(id, ownerUserId);
        // 明细以 code 冗余关联（无外键），删除即「跟随删除访问明细」，避免悬挂
        removeVisits(record.getCode());
        this.removeById(id);
        evictCache(record.getCode());
        log.info("短码已删除, code: {}, ownerUserId: {}", record.getCode(), ownerUserId);
    }

    @Override
    public IPage<ShortCodeStatsVo> pageVisits(Long id, Long ownerUserId, long page, long size) {
        ToolboxShortCode record = getOwnedRecord(id, ownerUserId);
        LambdaQueryWrapper<ToolboxShortVisit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ToolboxShortVisit::getCode, record.getCode())
                .orderByDesc(ToolboxShortVisit::getCreatedAt);
        IPage<ToolboxShortVisit> result = toolboxShortVisitService.page(new Page<>(page, size), wrapper);
        Page<ShortCodeStatsVo> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::buildStatsVo).collect(Collectors.toList()));
        return voPage;
    }

    // ==================== 跳转解析 ====================

    @Override
    public ResolveResult resolveForRedirect(String code) {
        // 1. 空值负缓存：只反映「库中无此码」，命中直接 404
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(MISS_KEY_PREFIX + code))) {
            return ResolveResult.notFound();
        }
        // 2. 跳转缓存：仅存「干净」记录（公开+类型1/2+无生命周期字段），命中直接返回目标
        String cached = stringRedisTemplate.opsForValue().get(REDIRECT_KEY_PREFIX + code);
        if (cached != null) {
            return ResolveResult.ok(cached);
        }
        // 3. DB 点查 uk_code
        ToolboxShortCode record = getByCode(code);
        // 4. 不存在 → 写空值标记防穿透
        if (record == null) {
            stringRedisTemplate.opsForValue()
                    .set(MISS_KEY_PREFIX + code, "1", Duration.ofSeconds(MISS_TTL_SECONDS));
            return ResolveResult.notFound();
        }
        // 5. 私有码：公开路径一律 404，不暴露存在性（owner 走带 JWT 的解析接口）
        if (!Integer.valueOf(PUBLIC_TRUE).equals(record.getIsPublic())) {
            return ResolveResult.notFound();
        }
        // 6. 生命周期判断（读路径，对齐 shlink isEnabled）
        if (isUnavailable(record)) {
            return ResolveResult.unavailable();
        }
        // 非跳转类型（私密文本/投递会话等 target 为空）一律 410，防 302 至空目标
        if (StrUtil.isBlank(record.getTarget())) {
            return ResolveResult.unavailable();
        }
        // 7. 仅「干净」记录进缓存；其余每次走 DB，换取生命周期语义绝对正确
        if (isClean(record)) {
            stringRedisTemplate.opsForValue()
                    .set(REDIRECT_KEY_PREFIX + code, record.getTarget(), Duration.ofMinutes(REDIRECT_TTL_MINUTES));
        }
        return ResolveResult.ok(record.getTarget());
    }

    @Override
    public ShortCodeVo resolveForOwner(String code, Long ownerUserId) {
        ToolboxShortCode record = getByCode(code);
        if (record == null) {
            throw new CustomExceptions("短码不存在");
        }
        if (!record.getOwnerUserId().equals(ownerUserId)) {
            // 归属不符按 404 语义处理，不暴露存在性；码真实存在，禁止写负缓存
            log.debug("短码归属校验不符, code: {}, ownerUserId: {}", code, ownerUserId);
            throw new CustomExceptions("短码不存在");
        }
        // 私密文本对 owner 始终可见（状态随 Vo 下发，焚毁/过期也可见）；其余类型沿用不可用即拒绝
        if (!Integer.valueOf(ShortCodeContentType.SECRET_TEXT).equals(record.getContentType()) && isUnavailable(record)) {
            throw new CustomExceptions("短码已过期或暂不可访问");
        }
        return buildVo(record);
    }

    // ==================== 私密文本（阅后即焚） ====================

    @Override
    public SecretMetaVo secretMeta(String code) {
        ToolboxShortCode record = getByCode(code);
        if (record == null || !Integer.valueOf(ShortCodeContentType.SECRET_TEXT).equals(record.getContentType())) {
            throw new CustomExceptions(CODE_NOT_FOUND, "私密文本不存在");
        }
        if (record.getBurnedAt() != null) {
            throw new CustomExceptions(CODE_GONE, "私密文本已焚毁");
        }
        if (isExpired(record)) {
            throw new CustomExceptions(CODE_GONE, "私密文本已过期");
        }
        return new SecretMetaVo().setRequiresPassword(StrUtil.isNotBlank(record.getPasswordHash()));
    }

    @Override
    @Transactional
    public SecretViewVo viewSecret(String code, String password) {
        // 行锁串行化并发揭示：两个并发 view 只有一个拿到内容（一次性语义的最终裁决）
        ToolboxShortCode record = this.getBaseMapper().selectOne(new LambdaQueryWrapper<ToolboxShortCode>()
                .eq(ToolboxShortCode::getCode, code)
                .last("FOR UPDATE"));
        if (record == null || !Integer.valueOf(ShortCodeContentType.SECRET_TEXT).equals(record.getContentType())) {
            throw new CustomExceptions(CODE_NOT_FOUND, "私密文本不存在");
        }
        if (record.getBurnedAt() != null) {
            throw new CustomExceptions(CODE_GONE, "私密文本已焚毁");
        }
        if (isExpired(record)) {
            throw new CustomExceptions(CODE_GONE, "私密文本已过期");
        }
        // 可选口令：BCrypt 校验，失败故意延迟防爆破（错误口令绝不焚毁，防爆破者烧毁他人内容）
        if (StrUtil.isNotBlank(record.getPasswordHash())
                && !passwordEncoder.matches(password == null ? "" : password, record.getPasswordHash())) {
            delayAgainstBruteForce();
            throw new CustomExceptions(CODE_FORBIDDEN, "口令错误");
        }
        // 条件焚毁（行锁内必成功，affected=0 仅作并发兜底防御）：焚毁状态与内容清空一次写入
        boolean burned = this.update(new LambdaUpdateWrapper<ToolboxShortCode>()
                .eq(ToolboxShortCode::getId, record.getId())
                .isNull(ToolboxShortCode::getBurnedAt)
                .set(ToolboxShortCode::getBurnedAt, LocalDateTime.now())
                .set(ToolboxShortCode::getContent, ""));
        if (!burned) {
            throw new CustomExceptions(CODE_GONE, "私密文本已焚毁");
        }
        String plaintext = toolboxGcmCipher.decrypt(record.getContent());
        log.info("私密文本已焚毁, code: {}", code);
        return new SecretViewVo().setContent(plaintext).setExpireAt(record.getExpireAt());
    }

    // ==================== 管理端 ====================

    @Override
    public IPage<AdminShortCodeVo> adminPage(SearchShortCodeFrom from) {
        LambdaQueryWrapper<ToolboxShortCode> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(from.getCode())) {
            wrapper.eq(ToolboxShortCode::getCode, from.getCode());
        }
        if (from.getContentType() != null) {
            wrapper.eq(ToolboxShortCode::getContentType, from.getContentType());
        }
        if (StrUtil.isNotBlank(from.getOwnerPhone())) {
            Users owner = usersMapper.selectOne(new LambdaQueryWrapper<Users>()
                    .eq(Users::getPhoneNumber, from.getOwnerPhone()));
            if (owner == null) {
                Page<AdminShortCodeVo> empty = new Page<>(from.getPage(), from.getSize(), 0);
                empty.setRecords(Collections.emptyList());
                return empty;
            }
            wrapper.eq(ToolboxShortCode::getOwnerUserId, owner.getUserId());
        }
        wrapper.orderByDesc(ToolboxShortCode::getCreatedAt);

        IPage<ToolboxShortCode> result = this.page(new Page<>(from.getPage(), from.getSize()), wrapper);
        Map<Long, Users> ownerMap = loadOwners(result.getRecords());

        Page<AdminShortCodeVo> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(record -> buildAdminVo(record, ownerMap.get(record.getOwnerUserId())))
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public AdminShortCodeVo adminDetail(Long id) {
        ToolboxShortCode record = this.getById(id);
        if (record == null) {
            throw new CustomExceptions("短码不存在");
        }
        Users owner = usersMapper.selectById(record.getOwnerUserId());
        AdminShortCodeVo vo = buildAdminVo(record, owner);
        // 最近访问明细摘要
        LambdaQueryWrapper<ToolboxShortVisit> visitWrapper = new LambdaQueryWrapper<>();
        visitWrapper.eq(ToolboxShortVisit::getCode, record.getCode())
                .orderByDesc(ToolboxShortVisit::getCreatedAt)
                .last("LIMIT 10");
        vo.setRecentVisits(toolboxShortVisitService.list(visitWrapper).stream()
                .map(this::buildStatsVo)
                .collect(Collectors.toList()));
        return vo;
    }

    @Override
    public void adminDelete(Long id) {
        ToolboxShortCode record = this.getById(id);
        if (record == null) {
            throw new CustomExceptions("短码不存在");
        }
        // 与用户删除同语义：跟随删除访问明细，避免悬挂
        removeVisits(record.getCode());
        this.removeById(id);
        evictCache(record.getCode());
        log.info("管理端删除短码, code: {}", record.getCode());
    }

    // ==================== 内部方法 ====================

    private LambdaQueryWrapper<ToolboxShortCode> ownerWrapper(SearchShortCodeFrom from, Long ownerUserId) {
        LambdaQueryWrapper<ToolboxShortCode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ToolboxShortCode::getOwnerUserId, ownerUserId);
        if (from.getContentType() != null) {
            wrapper.eq(ToolboxShortCode::getContentType, from.getContentType());
        }
        wrapper.orderByDesc(ToolboxShortCode::getCreatedAt);
        return wrapper;
    }

    private ToolboxShortCode getByCode(String code) {
        return this.getBaseMapper().selectOne(new LambdaQueryWrapper<ToolboxShortCode>()
                .eq(ToolboxShortCode::getCode, code));
    }

    private ToolboxShortCode getOwnedRecord(Long id, Long ownerUserId) {
        ToolboxShortCode record = this.getById(id);
        if (record == null || !record.getOwnerUserId().equals(ownerUserId)) {
            throw new CustomExceptions("短码不存在");
        }
        return record;
    }

    /** 安全底线（方案 §5.5）：target 必须可解析为 http/https URI，防开放重定向 */
    private String validateTarget(String target) {
        if (StrUtil.isBlank(target)) {
            throw new CustomExceptions("目标链接不能为空");
        }
        String trimmed = target.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new CustomExceptions("目标链接必须为 http/https 地址");
        }
        try {
            if (URI.create(trimmed).getHost() == null) {
                throw new CustomExceptions("目标链接必须为合法的 http/https 地址");
            }
        } catch (IllegalArgumentException e) {
            throw new CustomExceptions("目标链接必须为合法的 http/https 地址");
        }
        return trimmed;
    }

    /** 可见性归一：null 默认公开，非法值拒绝 */
    private int normalizeVisibility(Integer isPublic) {
        if (isPublic == null) {
            return PUBLIC_TRUE;
        }
        if (isPublic != PUBLIC_TRUE && isPublic != PUBLIC_FALSE) {
            throw new CustomExceptions("可见性参数非法");
        }
        return isPublic;
    }

    /** 私密文本正文校验并加密：非空 ≤2000 字符；AES-256-GCM 落库，库中无明文 */
    private String validateAndEncryptSecret(String content) {
        if (StrUtil.isBlank(content)) {
            throw new CustomExceptions("私密文本内容不能为空");
        }
        if (content.length() > MAX_SECRET_CHARS) {
            throw new CustomExceptions("私密文本过长（上限2000字符）");
        }
        return toolboxGcmCipher.encrypt(content, SECRET_KEY_VERSION);
    }

    /** 揭示失败防爆破延迟：固定 500ms，抹平响应时差 */
    private void delayAgainstBruteForce() {
        try {
            Thread.sleep(BRUTE_FORCE_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 私密文本状态：焚毁 > 过期 > 可用 */
    private String secretStateOf(ToolboxShortCode record) {
        if (record.getBurnedAt() != null) {
            return SecretState.BURNED;
        }
        return isExpired(record) ? SecretState.EXPIRED : SecretState.ACTIVE;
    }

    /** 私密文本分享链接：{toolbox.web-base-url}/s/{code}；未配置降级空串（端上仅展示短码） */
    private String secretUrlOf(String code) {
        String base = webBaseUrlResolver.resolve();
        if (StrUtil.isBlank(base)) {
            log.debug("未配置 toolbox.web-base-url，私密文本分享链接降级为空串: code={}", code);
            return "";
        }
        return StrUtil.removeSuffix(base, "/") + "/s/" + code;
    }

    /** 生命周期判断（读路径）：已焚毁 / 过期 / 未解锁 / 达访问上限 */
    private boolean isUnavailable(ToolboxShortCode record) {
        if (record.getBurnedAt() != null) {
            return true;
        }
        if (isExpired(record)) {
            return true;
        }
        if (record.getUnlockAt() != null && record.getUnlockAt().isAfter(LocalDateTime.now())) {
            return true;
        }
        return record.getMaxVisits() != null
                && record.getClickCount() != null
                && record.getClickCount() >= record.getMaxVisits();
    }

    /** 过期判断：expire_at 早于当前时刻 */
    private boolean isExpired(ToolboxShortCode record) {
        return record.getExpireAt() != null && record.getExpireAt().isBefore(LocalDateTime.now());
    }

    /** 「干净」记录：公开 + 类型1/2 + 无生命周期字段 —— 语义仅依赖 target 本身，可安全缓存 */
    private boolean isClean(ToolboxShortCode record) {
        boolean publicCode = Integer.valueOf(PUBLIC_TRUE).equals(record.getIsPublic());
        boolean shortLinkOrLive = ShortCodeContentType.SHORT_LINK == record.getContentType()
                || ShortCodeContentType.LIVE_CODE == record.getContentType();
        boolean noLifecycle = record.getExpireAt() == null
                && record.getUnlockAt() == null
                && record.getMaxVisits() == null;
        return publicCode && shortLinkOrLive && noLifecycle;
    }

    /** 缓存失效联动：更新/删除时清跳转缓存与负缓存 */
    private void evictCache(String code) {
        stringRedisTemplate.delete(REDIRECT_KEY_PREFIX + code);
        stringRedisTemplate.delete(MISS_KEY_PREFIX + code);
    }

    /** 跟随删除：按 code 清理访问明细（明细表无外键，避免悬挂） */
    private void removeVisits(String code) {
        toolboxShortVisitService.remove(new LambdaQueryWrapper<ToolboxShortVisit>().eq(ToolboxShortVisit::getCode, code));
    }

    private Map<Long, Users> loadOwners(List<ToolboxShortCode> records) {
        Set<Long> ownerIds = records.stream()
                .map(ToolboxShortCode::getOwnerUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ownerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return usersMapper.selectBatchIds(ownerIds).stream()
                .collect(Collectors.toMap(Users::getUserId, Function.identity()));
    }

    private String shortUrlOf(String code) {
        String base = publicBaseUrlResolver.resolve(currentRequest());
        if (StrUtil.isBlank(base)) {
            log.warn("未配置 toolbox.public-base-url 且无法从请求推导对外域名，短链退化为相对路径: code={}", code);
        }
        return StrUtil.removeSuffix(base, "/") + "/web/t/" + code;
    }

    /** 取当前请求；非请求线程（如定时任务）返回 null，由调用方降级 */
    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return (attributes instanceof ServletRequestAttributes servletRequestAttributes)
                ? servletRequestAttributes.getRequest()
                : null;
    }

    private ShortCodeVo buildVo(ToolboxShortCode record) {
        boolean secret = Integer.valueOf(ShortCodeContentType.SECRET_TEXT).equals(record.getContentType());
        return new ShortCodeVo()
                .setId(record.getId())
                .setCode(record.getCode())
                .setContentType(record.getContentType())
                .setTarget(record.getTarget())
                .setIsPublic(record.getIsPublic())
                .setUnlockAt(record.getUnlockAt())
                .setExpireAt(record.getExpireAt())
                .setMaxVisits(record.getMaxVisits())
                .setClickCount(record.getClickCount())
                .setShortUrl(shortUrlOf(record.getCode()))
                // 私密文本：状态与分享链接随 Vo 下发；content（含密文）永不回显
                .setSecretState(secret ? secretStateOf(record) : null)
                .setBurnedAt(record.getBurnedAt())
                .setSecretUrl(secret ? secretUrlOf(record.getCode()) : null)
                .setCreatedAt(record.getCreatedAt())
                .setUpdatedAt(record.getUpdatedAt());
    }

    private ShortCodeStatsVo buildStatsVo(ToolboxShortVisit visit) {
        return new ShortCodeStatsVo()
                .setId(visit.getId())
                .setCode(visit.getCode())
                .setIpHash(visit.getIpHash())
                .setUserAgent(visit.getUserAgent())
                .setReferer(visit.getReferer())
                .setCreatedAt(visit.getCreatedAt());
    }

    private AdminShortCodeVo buildAdminVo(ToolboxShortCode record, Users owner) {
        return new AdminShortCodeVo()
                .setId(record.getId())
                .setCode(record.getCode())
                .setContentType(record.getContentType())
                .setTarget(record.getTarget())
                .setOwnerUserId(record.getOwnerUserId())
                .setOwnerPhone(owner == null ? null : maskPhone(owner.getPhoneNumber()))
                .setIsPublic(record.getIsPublic())
                .setUnlockAt(record.getUnlockAt())
                .setExpireAt(record.getExpireAt())
                .setMaxVisits(record.getMaxVisits())
                .setClickCount(record.getClickCount())
                .setSecretState(Integer.valueOf(ShortCodeContentType.SECRET_TEXT).equals(record.getContentType())
                        ? secretStateOf(record) : null)
                .setBurnedAt(record.getBurnedAt())
                .setCreatedAt(record.getCreatedAt())
                .setUpdatedAt(record.getUpdatedAt());
    }

    /** 手机号脱敏：保留前 3 后 4 */
    private String maskPhone(String phone) {
        if (StrUtil.isBlank(phone) || phone.length() < 8) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
