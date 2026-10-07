package com.smartwash.toolbox.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.entity.Users;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.mapper.UsersMapper;
import com.smartwash.toolbox.entity.ToolboxShortCode;
import com.smartwash.toolbox.entity.ToolboxShortVisit;
import com.smartwash.toolbox.from.AddShortCodeFrom;
import com.smartwash.toolbox.from.SearchShortCodeFrom;
import com.smartwash.toolbox.from.UpdateShortCodeFrom;
import com.smartwash.toolbox.mapper.ToolboxShortCodeMapper;
import com.smartwash.toolbox.service.ToolboxShortCodeService.ResolveResult;
import com.smartwash.toolbox.service.ToolboxShortVisitService;
import com.smartwash.toolbox.toolkit.ShortCodeGenerator;
import com.smartwash.toolbox.vo.AdminShortCodeVo;
import com.smartwash.toolbox.vo.ShortCodeVo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 短码内核服务测试（方案 §十 2/3 项）。
 * 覆盖：随机码冲突重试、别名冲突/格式校验、target scheme 校验、resolve 全分支
 * （负缓存/跳转缓存/DB/私有 404/归属不符 404/生命周期 410/仅干净记录进缓存/更新删除清缓存）。
 * Redis 使用 mock 的 StringRedisTemplate + 内存 Map 还原真实 get/set/hasKey/delete 语义。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ToolboxShortCodeServiceImpl 创建/解析/缓存联动测试")
class ToolboxShortCodeServiceImplTest {

    private static final Long OWNER_ID = 10L;
    private static final Long OTHER_ID = 99L;
    private static final String REDIRECT_KEY_PREFIX = "smartwash:toolbox:redirect:";
    private static final String MISS_KEY_PREFIX = "smartwash:toolbox:miss:";

    @Mock
    private ToolboxShortCodeMapper shortCodeMapper;
    @Mock
    private ToolboxShortVisitService visitService;
    @Mock
    private UsersMapper usersMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    /** 模拟 Redis 的字符串存储，还原真实 get/set/hasKey/delete 语义 */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    private final ShortCodeGenerator generator = new ShortCodeGenerator();
    private ToolboxShortCodeServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // 纯单测环境无 MyBatis 容器：手动初始化实体 TableInfo，供断言前物化 wrapper 的 SQL 段
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ToolboxShortCode.class);
        TableInfoHelper.initTableInfo(assistant, ToolboxShortVisit.class);
    }

    @BeforeEach
    void setUp() {
        cache.clear();
        service = new ToolboxShortCodeServiceImpl(generator, visitService, stringRedisTemplate, usersMapper);
        // ServiceImpl 的 save/getById 等内置方法走父类 baseMapper 字段，需手动注入同一 mock
        ReflectionTestUtils.setField(service, "baseMapper", shortCodeMapper);
        ReflectionTestUtils.setField(service, "publicBaseUrl", "http://test.local");

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenAnswer(inv -> cache.get(inv.getArgument(0, String.class)));
        doAnswer(inv -> {
            cache.put(inv.getArgument(0, String.class), inv.getArgument(1, String.class));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(java.time.Duration.class));
        when(stringRedisTemplate.hasKey(anyString()))
                .thenAnswer(inv -> cache.containsKey(inv.getArgument(0, String.class)));
        when(stringRedisTemplate.delete(anyString()))
                .thenAnswer(inv -> cache.remove(inv.getArgument(0, String.class)) != null);
    }

    private AddShortCodeFrom addFrom(String target, String customCode) {
        AddShortCodeFrom from = new AddShortCodeFrom();
        from.setTarget(target);
        from.setCustomCode(customCode);
        return from;
    }

    private ToolboxShortCode record(String code, Integer isPublic) {
        return new ToolboxShortCode()
                .setId(1L)
                .setCode(code)
                .setContentType(1)
                .setTarget("https://example.com")
                .setOwnerUserId(OWNER_ID)
                .setIsPublic(isPublic)
                .setClickCount(0L);
    }

    private void stubDb(ToolboxShortCode record) {
        when(shortCodeMapper.selectOne(any(Wrapper.class))).thenReturn(record);
    }

    // ==================== 创建：唯一索引裁决 + 冲突重试 ====================

    @Test
    @DisplayName("创建随机码短码：字段完整落库并返回拼好的完整跳转 URL")
    void create_randomCode_buildsEntityAndShortUrl() {
        when(shortCodeMapper.insert(any(ToolboxShortCode.class))).thenReturn(1);

        ShortCodeVo vo = service.createShortCode(addFrom("https://example.com/page", null), OWNER_ID);

        ArgumentCaptor<ToolboxShortCode> captor = ArgumentCaptor.forClass(ToolboxShortCode.class);
        verify(shortCodeMapper).insert(captor.capture());
        ToolboxShortCode saved = captor.getValue();
        assertEquals(7, saved.getCode().length(), "随机短码长度恒为 7");
        assertEquals(1, saved.getContentType(), "M1 仅交付短链类型");
        assertEquals(OWNER_ID, saved.getOwnerUserId());
        assertEquals(1, saved.getIsPublic(), "默认公开");
        assertEquals("https://example.com/page", saved.getTarget());
        assertEquals("http://test.local/web/t/" + saved.getCode(), vo.getShortUrl(), "Vo 必须含拼好的完整跳转 URL");
    }

    @Test
    @DisplayName("创建自定义别名：直接使用别名，并清空残留负缓存防止新码被误判 404")
    void create_customCode_usesAliasAndClearsMissCache() {
        cache.put(MISS_KEY_PREFIX + "my-link", "1");
        when(shortCodeMapper.insert(any(ToolboxShortCode.class))).thenReturn(1);

        ShortCodeVo vo = service.createShortCode(addFrom("https://example.com", "my-link"), OWNER_ID);

        assertEquals("my-link", vo.getCode());
        assertTrue(!cache.containsKey(MISS_KEY_PREFIX + "my-link"), "创建成功必须清空负缓存");
    }

    @Test
    @DisplayName("随机码冲突：首次 insert 抛 DuplicateKeyException 时换码重试直至成功")
    void create_randomCodeConflict_retriesWithNewCode() {
        when(shortCodeMapper.insert(any(ToolboxShortCode.class)))
                .thenThrow(new DuplicateKeyException("uk_code"))
                .thenReturn(1);

        ShortCodeVo vo = service.createShortCode(addFrom("https://example.com", null), OWNER_ID);

        ArgumentCaptor<ToolboxShortCode> captor = ArgumentCaptor.forClass(ToolboxShortCode.class);
        verify(shortCodeMapper, times(2)).insert(captor.capture());
        List<String> codes = captor.getAllValues().stream().map(ToolboxShortCode::getCode).collect(Collectors.toList());
        assertNotEquals(codes.get(0), codes.get(1), "冲突后必须换一个新随机码");
        assertEquals(vo.getCode(), codes.get(1));
    }

    @Test
    @DisplayName("随机码连续冲突达上限（10 次）：报「短码生成繁忙」")
    void create_retryExhausted_failsWithBusyMessage() {
        when(shortCodeMapper.insert(any(ToolboxShortCode.class)))
                .thenThrow(new DuplicateKeyException("uk_code"));

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.createShortCode(addFrom("https://example.com", null), OWNER_ID));

        assertEquals("短码生成繁忙，请重试", ex.getMessage());
        verify(shortCodeMapper, times(10)).insert(any(ToolboxShortCode.class));
    }

    @Test
    @DisplayName("别名冲突：不重试，直接报「该短码已被占用」")
    void create_customCodeConflict_failsImmediately() {
        when(shortCodeMapper.insert(any(ToolboxShortCode.class)))
                .thenThrow(new DuplicateKeyException("uk_code"));

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.createShortCode(addFrom("https://example.com", "taken"), OWNER_ID));

        assertEquals("该短码已被占用", ex.getMessage());
        verify(shortCodeMapper, times(1)).insert(any(ToolboxShortCode.class));
    }

    @Test
    @DisplayName("别名格式非法：拒绝创建且不触库")
    void create_invalidAliasFormat_rejected() {
        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.createShortCode(addFrom("https://example.com", "ab"), OWNER_ID));

        assertEquals("自定义短码须为4-16位字母数字或-_", ex.getMessage());
        verify(shortCodeMapper, never()).insert(any(ToolboxShortCode.class));
    }

    @Test
    @DisplayName("target scheme 校验：拒绝非 http/https 与无 host 的目标（防开放重定向）")
    void create_invalidTargetScheme_rejected() {
        for (String bad : new String[]{"ftp://example.com", "javascript:alert(1)", "http://", "https:///path", " "}) {
            CustomExceptions ex = assertThrows(CustomExceptions.class,
                    () -> service.createShortCode(addFrom(bad, null), OWNER_ID),
                    "target=" + bad + " 必须被拒绝");
            assertTrue(ex.getMessage().contains("目标链接"));
        }
        verify(shortCodeMapper, never()).insert(any(ToolboxShortCode.class));
    }

    // ==================== resolve：缓存链路与生命周期 ====================

    @Test
    @DisplayName("resolve：负缓存命中直接 404，不打 DB")
    void resolve_negativeCacheHit_returns404WithoutDb() {
        cache.put(MISS_KEY_PREFIX + "nope123", "1");

        ResolveResult r = service.resolveForRedirect("nope123");

        assertTrue(r.isNotFound());
        verify(shortCodeMapper, never()).selectOne(any(Wrapper.class));
    }

    @Test
    @DisplayName("resolve：跳转缓存命中直接返回目标，不打 DB")
    void resolve_redirectCacheHit_returnsCachedTarget() {
        cache.put(REDIRECT_KEY_PREFIX + "cache01", "https://cached.example");

        ResolveResult r = service.resolveForRedirect("cache01");

        assertTrue(!r.isNotFound() && !r.isUnavailable());
        assertEquals("https://cached.example", r.getTarget());
        verify(shortCodeMapper, never()).selectOne(any(Wrapper.class));
    }

    @Test
    @DisplayName("resolve：DB 未命中写空值负缓存并 404")
    void resolve_dbMiss_writesNegativeCache() {
        when(shortCodeMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        ResolveResult r = service.resolveForRedirect("ghost01");

        assertTrue(r.isNotFound());
        assertEquals("1", cache.get(MISS_KEY_PREFIX + "ghost01"), "未命中必须写空值负缓存防穿透");
    }

    @Test
    @DisplayName("resolve：私有码在公开路径一律 404，不写任何缓存（不暴露存在性）")
    void resolve_privateCode_returns404WithoutCache() {
        stubDb(record("priv01", 2));

        ResolveResult r = service.resolveForRedirect("priv01");

        assertTrue(r.isNotFound());
        assertTrue(!cache.containsKey(REDIRECT_KEY_PREFIX + "priv01"), "私有码不得进跳转缓存");
        assertTrue(!cache.containsKey(MISS_KEY_PREFIX + "priv01"), "码真实存在，不得写负缓存");
    }

    @Test
    @DisplayName("resolve：已过期（expire_at 已过）返回 410")
    void resolve_expired_returns410() {
        ToolboxShortCode expired = record("expir01", 1)
                .setExpireAt(LocalDateTime.now().minusHours(1));
        stubDb(expired);

        assertTrue(service.resolveForRedirect("expir01").isUnavailable());
    }

    @Test
    @DisplayName("resolve：未解锁（unlock_at 未到）返回 410")
    void resolve_locked_returns410() {
        ToolboxShortCode locked = record("lock001", 1)
                .setUnlockAt(LocalDateTime.now().plusHours(1));
        stubDb(locked);

        assertTrue(service.resolveForRedirect("lock001").isUnavailable());
    }

    @Test
    @DisplayName("resolve：达到访问上限（click_count >= max_visits）返回 410")
    void resolve_maxVisitsReached_returns410() {
        ToolboxShortCode limited = record("limit01", 1)
                .setMaxVisits(10L)
                .setClickCount(10L);
        stubDb(limited);

        assertTrue(service.resolveForRedirect("limit01").isUnavailable());
    }

    @Test
    @DisplayName("resolve：「干净」记录（公开+类型1/2+无生命周期字段）才写跳转缓存")
    void resolve_cleanRecord_onlyCached() {
        stubDb(record("clean01", 1));

        ResolveResult r = service.resolveForRedirect("clean01");

        assertEquals("https://example.com", r.getTarget());
        assertEquals("https://example.com", cache.get(REDIRECT_KEY_PREFIX + "clean01"), "干净记录必须进跳转缓存");
    }

    @Test
    @DisplayName("resolve：活码（类型2）的干净记录同样可缓存")
    void resolve_liveCodeCleanRecord_cached() {
        stubDb(record("live001", 1).setContentType(2));

        ResolveResult r = service.resolveForRedirect("live001");

        assertEquals("https://example.com", r.getTarget());
        assertNotNull(cache.get(REDIRECT_KEY_PREFIX + "live001"));
    }

    @Test
    @DisplayName("resolve：带生命周期字段的记录（未过期）可用但绝不进缓存")
    void resolve_recordWithLifecycle_notCached() {
        stubDb(record("life001", 1).setExpireAt(LocalDateTime.now().plusDays(7)));

        ResolveResult r = service.resolveForRedirect("life001");

        assertEquals("https://example.com", r.getTarget());
        assertTrue(!cache.containsKey(REDIRECT_KEY_PREFIX + "life001"), "带生命周期的记录不得进跳转缓存");
    }

    @Test
    @DisplayName("resolve：带未达上限的 max_visits 的记录可用但绝不进缓存")
    void resolve_recordWithMaxVisits_notCached() {
        stubDb(record("half001", 1).setMaxVisits(100L).setClickCount(1L));

        ResolveResult r = service.resolveForRedirect("half001");

        assertEquals("https://example.com", r.getTarget());
        assertTrue(!cache.containsKey(REDIRECT_KEY_PREFIX + "half001"));
    }

    // ==================== owner 解析：归属校验 ====================

    @Test
    @DisplayName("owner 解析：私有码归属相符可取到目标（私有码唯一取值通道）")
    void resolveOwner_privateCodeOwnedByCaller_returnsVo() {
        stubDb(record("mine001", 2));

        ShortCodeVo vo = service.resolveForOwner("mine001", OWNER_ID);

        assertEquals("https://example.com", vo.getTarget());
        assertEquals("http://test.local/web/t/mine001", vo.getShortUrl());
    }

    @Test
    @DisplayName("owner 解析：归属不符按 404 语义处理，且不写负缓存（码真实存在）")
    void resolveOwner_notOwned_treatedAs404WithoutNegativeCache() {
        stubDb(record("theirs", 2));

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.resolveForOwner("theirs", OTHER_ID));

        assertEquals("短码不存在", ex.getMessage(), "归属不符不得暴露存在性");
        assertTrue(!cache.containsKey(MISS_KEY_PREFIX + "theirs"), "归属不符不得污染负缓存");
    }

    @Test
    @DisplayName("owner 解析：不存在的短码报「短码不存在」")
    void resolveOwner_missing_throws() {
        when(shortCodeMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThrows(CustomExceptions.class, () -> service.resolveForOwner("ghost99", OWNER_ID));
    }

    @Test
    @DisplayName("owner 解析：已过期短码报不可访问")
    void resolveOwner_expired_throws() {
        stubDb(record("old0001", 1).setExpireAt(LocalDateTime.now().minusDays(1)));

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.resolveForOwner("old0001", OWNER_ID));

        assertEquals("短码已过期或暂不可访问", ex.getMessage());
    }

    // ==================== 更新 / 删除：缓存失效联动 ====================

    @Test
    @DisplayName("更新 target：落库后清空跳转缓存与负缓存，旧码立即跳新目标")
    void updateTarget_evictsCache() {
        cache.put(REDIRECT_KEY_PREFIX + "upd0001", "https://old.example");
        cache.put(MISS_KEY_PREFIX + "upd0001", "1");
        when(shortCodeMapper.selectById(1L)).thenReturn(record("upd0001", 1));
        when(shortCodeMapper.updateById(any(ToolboxShortCode.class))).thenReturn(1);

        UpdateShortCodeFrom from = new UpdateShortCodeFrom();
        from.setTarget("https://new.example");
        ShortCodeVo vo = service.updateShortCode(1L, from, OWNER_ID);

        assertEquals("https://new.example", vo.getTarget());
        assertTrue(!cache.containsKey(REDIRECT_KEY_PREFIX + "upd0001"), "更新后必须 DEL 跳转缓存");
        assertTrue(!cache.containsKey(MISS_KEY_PREFIX + "upd0001"), "更新后必须 DEL 负缓存");
    }

    @Test
    @DisplayName("更新：短码不存在或非本人所有时拒绝")
    void update_notOwned_rejected() {
        stubDb(record("nope001", 1).setOwnerUserId(OTHER_ID));

        UpdateShortCodeFrom from = new UpdateShortCodeFrom();
        from.setTarget("https://new.example");
        assertThrows(CustomExceptions.class, () -> service.updateShortCode(1L, from, OWNER_ID));
        verify(shortCodeMapper, never()).updateById(any(ToolboxShortCode.class));
    }

    @Test
    @DisplayName("删除：本人短码物理删除并清缓存；删除后 resolve 404")
    void delete_owned_removesAndEvictsCache() {
        cache.put(REDIRECT_KEY_PREFIX + "del0001", "https://example.com");
        ToolboxShortCode record = record("del0001", 1);
        when(shortCodeMapper.selectById(1L)).thenReturn(record);

        service.deleteShortCode(1L, OWNER_ID);

        verify(shortCodeMapper).deleteById(1L);
        assertTrue(!cache.containsKey(REDIRECT_KEY_PREFIX + "del0001"), "删除后必须清跳转缓存");

        // 删除后：DB 查无 → 404
        when(shortCodeMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertTrue(service.resolveForRedirect("del0001").isNotFound());
    }

    @Test
    @DisplayName("删除：非本人短码拒绝删除")
    void delete_notOwned_rejected() {
        when(shortCodeMapper.selectById(1L)).thenReturn(record("nope001", 1).setOwnerUserId(OTHER_ID));

        assertThrows(CustomExceptions.class, () -> service.deleteShortCode(1L, OWNER_ID));
        verify(shortCodeMapper, never()).deleteById(anyLong());
    }

    // ==================== 管理端 ====================

    @Test
    @DisplayName("管理端分页：返回含脱敏 owner 手机号与全部生命周期字段")
    void adminPage_mapsOwnerPhoneMasked() {
        ToolboxShortCode record = record("admin01", 2)
                .setExpireAt(LocalDateTime.now().plusDays(3))
                .setMaxVisits(50L)
                .setClickCount(7L);
        Page<ToolboxShortCode> result = new Page<>(1, 10, 1);
        result.setRecords(List.of(record));
        doReturn(result).when(shortCodeMapper).selectPage(any(), any());
        Users owner = new Users();
        owner.setUserId(OWNER_ID);
        owner.setPhoneNumber("13812340000");
        when(usersMapper.selectBatchIds(any())).thenReturn(List.of(owner));

        Page<AdminShortCodeVo> page = (Page<AdminShortCodeVo>) service.adminPage(new SearchShortCodeFrom());

        assertEquals(1, page.getRecords().size());
        AdminShortCodeVo vo = page.getRecords().get(0);
        assertEquals("138****0000", vo.getOwnerPhone(), "owner 手机号必须脱敏");
        assertEquals(2, vo.getIsPublic(), "管理员视角不受 is_public 限制（私有码可见）");
        assertEquals(7L, vo.getClickCount());
    }

    @Test
    @DisplayName("管理端分页：code/contentType 筛选条件生效")
    void adminPage_filtersApplied() {
        Page<ToolboxShortCode> result = new Page<>(1, 10, 0);
        result.setRecords(List.of());
        doReturn(result).when(shortCodeMapper).selectPage(any(), any());

        SearchShortCodeFrom from = new SearchShortCodeFrom();
        from.setCode("admin01");
        from.setContentType(1);
        service.adminPage(from);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<ToolboxShortCode>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(shortCodeMapper).selectPage(any(), captor.capture());
        captor.getValue().getSqlSegment(); // 物化惰性 SQL 段，条件参数才写入 paramNameValuePairs
        Map<String, Object> params = captor.getValue().getParamNameValuePairs();
        assertTrue(params.containsValue("admin01"), "code 筛选必须进入查询条件");
        assertTrue(params.containsValue(1), "contentType 筛选必须进入查询条件");
    }

    @Test
    @DisplayName("管理端分页：ownerPhone 查无此人时返回空页，不打短码表")
    void adminPage_unknownOwnerPhone_returnsEmptyPage() {
        when(usersMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        SearchShortCodeFrom from = new SearchShortCodeFrom();
        from.setOwnerPhone("19999999999");
        Page<AdminShortCodeVo> page = (Page<AdminShortCodeVo>) service.adminPage(from);

        assertEquals(0, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
        verify(shortCodeMapper, never()).selectPage(any(), any());
    }

    @Test
    @DisplayName("管理端详情：含最近访问明细摘要")
    void adminDetail_includesRecentVisits() {
        when(shortCodeMapper.selectById(1L)).thenReturn(record("detail1", 1));
        Users owner = new Users();
        owner.setUserId(OWNER_ID);
        owner.setPhoneNumber("13812340000");
        when(usersMapper.selectById(OWNER_ID)).thenReturn(owner);
        when(visitService.list(any(Wrapper.class))).thenReturn(List.of(new ToolboxShortVisit()
                .setId(5L).setCode("detail1").setIpHash("abcd1234").setUserAgent("Mozilla/5.0")));

        AdminShortCodeVo vo = service.adminDetail(1L);

        assertEquals("138****0000", vo.getOwnerPhone());
        assertEquals(1, vo.getRecentVisits().size());
        assertEquals("abcd1234", vo.getRecentVisits().get(0).getIpHash());
    }

    @Test
    @DisplayName("管理端删除：物理删除并清缓存；非 admin 场景由 Security 层拦截（此处验证删除语义）")
    void adminDelete_removesAndEvictsCache() {
        cache.put(REDIRECT_KEY_PREFIX + "adm0001", "https://example.com");
        when(shortCodeMapper.selectById(1L)).thenReturn(record("adm0001", 2));

        service.adminDelete(1L);

        verify(shortCodeMapper).deleteById(1L);
        assertTrue(!cache.containsKey(REDIRECT_KEY_PREFIX + "adm0001"), "管理端删除必须清跳转缓存");
    }

    @Test
    @DisplayName("管理端删除：短码不存在时拒绝")
    void adminDelete_missing_rejected() {
        when(shortCodeMapper.selectById(404L)).thenReturn(null);

        assertThrows(CustomExceptions.class, () -> service.adminDelete(404L));
        verify(shortCodeMapper, never()).deleteById(anyLong());
    }
}
