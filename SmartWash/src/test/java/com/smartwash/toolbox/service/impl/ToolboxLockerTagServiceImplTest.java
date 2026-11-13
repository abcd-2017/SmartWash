package com.smartwash.toolbox.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smartwash.entity.Lockers;
import com.smartwash.entity.Schools;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.mapper.LockersMapper;
import com.smartwash.mapper.SchoolsMapper;
import com.smartwash.toolbox.entity.ToolboxShortCode;
import com.smartwash.toolbox.mapper.ToolboxShortCodeMapper;
import com.smartwash.toolbox.service.ToolboxShortCodeService;
import com.smartwash.toolbox.toolkit.WebBaseUrlResolver;
import com.smartwash.toolbox.vo.LockerTagBatchItemVo;
import com.smartwash.toolbox.vo.LockerTagVo;
import com.smartwash.toolbox.vo.ShortCodeVo;
import org.apache.ibatis.builder.MapperBuilderAssistant;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 储物柜资产码服务测试：别名解析/绑定一致性、幂等生成（存在复用）、按校批量、
 * 未配置 web-base-url 拒绝生成。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ToolboxLockerTagServiceImpl 柜码生成/解析测试")
class ToolboxLockerTagServiceImplTest {

    private static final Long LOCKER_ID = 42L;
    private static final Long SCHOOL_ID = 7L;
    private static final String ALIAS = "L42";

    @Mock
    private ToolboxShortCodeService shortCodeService;
    @Mock
    private ToolboxShortCodeMapper shortCodeMapper;
    @Mock
    private LockersMapper lockersMapper;
    @Mock
    private SchoolsMapper schoolsMapper;
    @Mock
    private WebBaseUrlResolver webBaseUrlResolver;

    private ToolboxLockerTagServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ToolboxShortCode.class);
    }

    @BeforeEach
    void setUp() {
        service = new ToolboxLockerTagServiceImpl(shortCodeService, shortCodeMapper,
                lockersMapper, schoolsMapper, webBaseUrlResolver);
        when(webBaseUrlResolver.resolve()).thenReturn("https://web.test.local");
        when(lockersMapper.selectById(LOCKER_ID)).thenReturn(new Lockers()
                .setLockerId(LOCKER_ID)
                .setSchoolId(SCHOOL_ID)
                .setLockerNumber(12)
                .setStatus("0"));
        when(schoolsMapper.selectById(SCHOOL_ID)).thenReturn(new Schools()
                .setSchoolId(SCHOOL_ID)
                .setSchoolName("测试一中")
                .setLocation("衡阳市测试路 1 号"));
        when(shortCodeService.createShortCode(any(), eq(0L))).thenAnswer(inv -> {
            com.smartwash.toolbox.from.AddShortCodeFrom f = inv.getArgument(0);
            return new ShortCodeVo()
                    .setId(1L)
                    .setCode(f.getCustomCode())
                    .setContentType(2)
                    .setShortUrl("http://test.local/web/t/" + f.getCustomCode());
        });
    }

    private ToolboxShortCode liveTag(String target) {
        return new ToolboxShortCode()
                .setId(1L)
                .setCode(ALIAS)
                .setContentType(2)
                .setTarget(target)
                .setOwnerUserId(0L)
                .setIsPublic(1);
    }

    @Test
    @DisplayName("生成柜码：活码类型 + target 指向落地页 + 别名 L{lockerId} + owner=0")
    void generate_forLocker_buildsLiveCode() {
        when(shortCodeMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        ShortCodeVo vo = service.generateForLocker(LOCKER_ID);

        ArgumentCaptor<com.smartwash.toolbox.from.AddShortCodeFrom> captor =
                ArgumentCaptor.forClass(com.smartwash.toolbox.from.AddShortCodeFrom.class);
        verify(shortCodeService).createShortCode(captor.capture(), eq(0L));
        assertEquals(2, captor.getValue().getContentType(), "柜码为活码（contentType=2 首次落地）");
        assertEquals("https://web.test.local/l/" + LOCKER_ID, captor.getValue().getTarget());
        assertEquals(ALIAS, captor.getValue().getCustomCode());
        assertEquals(ALIAS, vo.getCode());
    }

    @Test
    @DisplayName("生成柜码幂等：已有同别名活码直接复用，不再插入")
    void generate_forLocker_idempotent() {
        when(shortCodeMapper.selectOne(any(Wrapper.class)))
                .thenReturn(liveTag("https://web.test.local/l/" + LOCKER_ID));
        when(shortCodeService.resolveForOwner(eq(ALIAS), eq(0L))).thenReturn(new ShortCodeVo().setCode(ALIAS));

        ShortCodeVo vo = service.generateForLocker(LOCKER_ID);

        assertEquals(ALIAS, vo.getCode());
        verify(shortCodeService, times(0)).createShortCode(any(), any());
    }

    @Test
    @DisplayName("未配置 web-base-url：生成被拒")
    void generate_withoutWebBase_rejected() {
        when(webBaseUrlResolver.resolve()).thenReturn("");
        assertThrows(CustomExceptions.class, () -> service.generateForLocker(LOCKER_ID));
    }

    @Test
    @DisplayName("解析柜码：返回柜子信息卡（校名/地址/柜号/状态）")
    void resolve_returnsLockerInfo() {
        when(shortCodeMapper.selectOne(any(Wrapper.class)))
                .thenReturn(liveTag("https://web.test.local/l/" + LOCKER_ID));

        LockerTagVo vo = service.resolve(ALIAS);

        assertEquals(LOCKER_ID, vo.getLockerId());
        assertEquals(12, vo.getLockerNumber());
        assertEquals("测试一中", vo.getSchoolName());
        assertEquals("衡阳市测试路 1 号", vo.getSchoolLocation());
    }

    @Test
    @DisplayName("解析柜码：非活码/绑定不符/非 L{lockerId} 形态一律 404")
    void resolve_mismatched_gone() {
        // 码存在但类型非活码
        when(shortCodeMapper.selectOne(any(Wrapper.class))).thenReturn(liveTag(null).setContentType(1));
        assertThrows(CustomExceptions.class, () -> service.resolve(ALIAS));

        // 活码但 target 绑定别的柜子
        when(shortCodeMapper.selectOne(any(Wrapper.class)))
                .thenReturn(liveTag("https://web.test.local/l/999"));
        assertThrows(CustomExceptions.class, () -> service.resolve(ALIAS));

        // 非 L{lockerId} 形态（真实短码走 /web/t 跳转，不落本接口）
        assertThrows(CustomExceptions.class, () -> service.resolve("Ab3x9K2"));
    }

    @Test
    @DisplayName("按校批量：逐柜幂等生成，结果含 created 标记")
    void generate_forSchool_batch() {
        when(lockersMapper.selectList(any())).thenReturn(List.of(
                new Lockers().setLockerId(LOCKER_ID).setSchoolId(SCHOOL_ID).setLockerNumber(1).setStatus("0"),
                new Lockers().setLockerId(43L).setSchoolId(SCHOOL_ID).setLockerNumber(2).setStatus("0")));
        when(lockersMapper.selectById(43L)).thenReturn(new Lockers()
                .setLockerId(43L).setSchoolId(SCHOOL_ID).setLockerNumber(2).setStatus("0"));
        // 两柜均无既有活码 → 全部新建，created=true
        when(shortCodeMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        List<LockerTagBatchItemVo> items = service.generateForSchool(SCHOOL_ID);

        assertEquals(2, items.size());
        assertTrue(items.get(0).isCreated());
        assertTrue(items.get(1).isCreated());
        assertEquals("L43", items.get(1).getCode());
    }
}
