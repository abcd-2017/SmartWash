package com.smartwash.toolbox.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import com.smartwash.toolbox.toolkit.ShortCodeGenerator;
import com.smartwash.toolbox.vo.AdminShortCodeVo;
import com.smartwash.toolbox.vo.ShortCodeStatsVo;
import com.smartwash.toolbox.vo.ShortCodeVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
    /** 内容类型：M1 仅交付 1 短链 */
    private static final int CONTENT_TYPE_SHORT_LINK = 1;
    private static final int CONTENT_TYPE_LIVE_CODE = 2;
    /** 可见性：1公开 2私有 */
    private static final int PUBLIC_TRUE = 1;
    private static final int PUBLIC_FALSE = 2;

    private static final String REDIRECT_KEY_PREFIX = "smartwash:toolbox:redirect:";
    private static final String MISS_KEY_PREFIX = "smartwash:toolbox:miss:";

    private final ShortCodeGenerator shortCodeGenerator;
    private final ToolboxShortVisitService toolboxShortVisitService;
    private final StringRedisTemplate stringRedisTemplate;
    private final UsersMapper usersMapper;

    /** 短码对外完整跳转 URL 的域名前缀，生产通过环境变量 TOOLBOX_PUBLIC_BASE_URL 注入 */
    @Value("${toolbox.public-base-url}")
    private String publicBaseUrl;

    // ==================== 创建（唯一索引裁决 + 冲突重试） ====================

    @Override
    public ShortCodeVo createShortCode(AddShortCodeFrom from, Long ownerUserId) {
        String target = validateTarget(from.getTarget());

        String customCode = from.getCustomCode();
        if (customCode != null && !shortCodeGenerator.validCustomCode(customCode)) {
            throw new CustomExceptions("自定义短码须为4-16位字母数字或-_");
        }
        Integer isPublic = from.getIsPublic() == null ? PUBLIC_TRUE : from.getIsPublic();
        if (isPublic != PUBLIC_TRUE && isPublic != PUBLIC_FALSE) {
            throw new CustomExceptions("可见性参数非法");
        }

        // 别名直接使用，随机码冲突时由唯一索引裁决后重生成（禁止先查后插，存在 TOCTOU 竞态窗口）
        String code = StrUtil.blankToDefault(customCode, shortCodeGenerator.randomCode());
        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                ToolboxShortCode entity = new ToolboxShortCode()
                        .setCode(code)
                        .setContentType(CONTENT_TYPE_SHORT_LINK)
                        .setTarget(target)
                        .setOwnerUserId(ownerUserId)
                        .setIsPublic(isPublic)
                        .setExpireAt(from.getExpireAt());
                // 单条 insert 自带原子性，不套外层事务
                this.save(entity);
                // 清空可能残留的负缓存，防止刚创建的码在 60s 内被误判 404
                stringRedisTemplate.delete(MISS_KEY_PREFIX + code);
                log.info("短码创建成功, code: {}, ownerUserId: {}, isPublic: {}", code, ownerUserId, isPublic);
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
        if (from.getTarget() != null) {
            record.setTarget(validateTarget(from.getTarget()));
        }
        if (from.getExpireAt() != null) {
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
        if (isUnavailable(record)) {
            throw new CustomExceptions("短码已过期或暂不可访问");
        }
        return buildVo(record);
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

    /** 生命周期判断（读路径）：过期 / 未解锁 / 达访问上限 */
    private boolean isUnavailable(ToolboxShortCode record) {
        LocalDateTime now = LocalDateTime.now();
        if (record.getExpireAt() != null && record.getExpireAt().isBefore(now)) {
            return true;
        }
        if (record.getUnlockAt() != null && record.getUnlockAt().isAfter(now)) {
            return true;
        }
        return record.getMaxVisits() != null
                && record.getClickCount() != null
                && record.getClickCount() >= record.getMaxVisits();
    }

    /** 「干净」记录：公开 + 类型1/2 + 无生命周期字段 —— 语义仅依赖 target 本身，可安全缓存 */
    private boolean isClean(ToolboxShortCode record) {
        boolean publicCode = Integer.valueOf(PUBLIC_TRUE).equals(record.getIsPublic());
        boolean shortLinkOrLive = Integer.valueOf(CONTENT_TYPE_SHORT_LINK).equals(record.getContentType())
                || Integer.valueOf(CONTENT_TYPE_LIVE_CODE).equals(record.getContentType());
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
        String base = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return base + "/web/t/" + code;
    }

    private ShortCodeVo buildVo(ToolboxShortCode record) {
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
