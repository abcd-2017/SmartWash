package com.smartwash.toolbox.delivery.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.from.BaseSearchFrom;
import com.smartwash.toolbox.delivery.entity.ToolboxDeliveryItem;
import com.smartwash.toolbox.delivery.toolkit.DeliveryObjectStorage;
import com.smartwash.toolbox.delivery.vo.DeliveryItemVo;
import com.smartwash.toolbox.entity.ToolboxShortCode;
import com.smartwash.toolbox.mapper.ToolboxShortCodeMapper;
import com.smartwash.toolbox.service.ToolboxShortCodeService;
import com.smartwash.toolbox.toolkit.ShortCodeGenerator;
import com.smartwash.toolbox.toolkit.WebBaseUrlResolver;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备投递服务测试（纯 Mockito + spy）。
 * 覆盖条目明细分页：归属校验、page/size 透传、VO 转换（含 hasContent 口径）；
 * 其余投递语义（认领/焚毁/占用）已有集成测试覆盖，不在本类重复。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ToolboxDeliveryServiceImpl 投递条目分页测试")
class ToolboxDeliveryServiceImplTest {

    private static final Long OWNER = 10L;
    private static final String CODE = "dEl1234";

    @Mock
    private ShortCodeGenerator shortCodeGenerator;
    @Mock
    private ToolboxShortCodeService toolboxShortCodeService;
    @Mock
    private ToolboxShortCodeMapper toolboxShortCodeMapper;
    @Mock
    private DeliveryObjectStorage deliveryObjectStorage;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private PasswordEncoder passwordEncoder;

    private ToolboxDeliveryServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ToolboxShortCode.class);
        TableInfoHelper.initTableInfo(assistant, ToolboxDeliveryItem.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new ToolboxDeliveryServiceImpl(shortCodeGenerator, toolboxShortCodeService,
                toolboxShortCodeMapper, deliveryObjectStorage, stringRedisTemplate, passwordEncoder,
                new WebBaseUrlResolver()));
    }

    private ToolboxShortCode session(Long ownerUserId) {
        return new ToolboxShortCode().setCode(CODE).setContentType(6)
                .setOwnerUserId(ownerUserId).setExpireAt(LocalDateTime.now().plusMinutes(10));
    }

    @Test
    @DisplayName("条目分页：归属通过后透传 page/size，按库内顺序转 VO（文本 hasContent / 焚毁占位 false）")
    void pageOwnerItems_returnsPagedVos() {
        when(toolboxShortCodeMapper.selectOne(any(Wrapper.class))).thenReturn(session(OWNER));
        ToolboxDeliveryItem text = new ToolboxDeliveryItem().setId(1L).setSessionCode(CODE)
                .setDirection(1).setKind(1).setTitle("文本").setTextContent("正文").setTaken(0)
                .setCreatedAt(LocalDateTime.now());
        ToolboxDeliveryItem burned = new ToolboxDeliveryItem().setId(2L).setSessionCode(CODE)
                .setDirection(2).setKind(3).setTitle("文件").setTextContent(null).setTaken(1)
                .setObjectSize(1024L).setCreatedAt(LocalDateTime.now());
        Page<ToolboxDeliveryItem> entityPage = new Page<>(1, 10, 2);
        entityPage.setRecords(List.of(text, burned));
        doReturn(entityPage).when(service).page(any(Page.class), any(Wrapper.class));

        Page<DeliveryItemVo> result = service.pageOwnerItems(CODE, new BaseSearchFrom(), OWNER);

        assertEquals(2L, result.getTotal());
        assertEquals(1L, result.getRecords().get(0).getId());
        assertTrue(result.getRecords().get(0).getHasContent(), "未焚毁文本应 hasContent=true");
        assertEquals(2L, result.getRecords().get(1).getId());
        assertFalse(result.getRecords().get(1).getHasContent(), "已焚毁占位应 hasContent=false");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Page<ToolboxDeliveryItem>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(service).page(pageCaptor.capture(), any(Wrapper.class));
        assertEquals(1L, pageCaptor.getValue().getCurrent());
        assertEquals(10L, pageCaptor.getValue().getSize());
    }

    @Test
    @DisplayName("条目分页：非 owner 一律 404 语义，不触达分页查询")
    void pageOwnerItems_nonOwner_404() {
        when(toolboxShortCodeMapper.selectOne(any(Wrapper.class))).thenReturn(session(OWNER));

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.pageOwnerItems(CODE, new BaseSearchFrom(), 99L));

        assertEquals("投递会话不存在或已失效", ex.getMessage());
        verify(service, never()).page(any(Page.class), any(Wrapper.class));
    }
}
