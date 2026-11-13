package com.smartwash.toolbox.assettag.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.crypto.ToolboxGcmCipher;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.toolbox.assettag.entity.ToolboxAssetTag;
import com.smartwash.toolbox.assettag.from.CreateAssetTagFrom;
import com.smartwash.toolbox.assettag.from.UpdateAssetTagFrom;
import com.smartwash.toolbox.assettag.mapper.ToolboxAssetTagMapper;
import com.smartwash.toolbox.assettag.vo.AssetTagPublicVo;
import com.smartwash.toolbox.assettag.vo.AssetTagVo;
import com.smartwash.toolbox.entity.ToolboxShortCode;
import com.smartwash.toolbox.entity.ToolboxShortVisit;
import com.smartwash.toolbox.from.AddShortCodeFrom;
import com.smartwash.toolbox.mapper.ToolboxShortCodeMapper;
import com.smartwash.toolbox.service.ToolboxShortCodeService;
import com.smartwash.toolbox.service.ToolboxShortVisitService;
import com.smartwash.toolbox.toolkit.WebBaseUrlResolver;
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
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 物品标签服务测试（一物一码 M2）。
 * 覆盖：创建加密落库与强制字段、公开解析「状态×联系方式」下发矩阵、更新（状态裁定/清空联系方式）、
 * 删除级联、管理端治理删除类型校验。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ToolboxAssetTagServiceImpl 物品标签测试")
class ToolboxAssetTagServiceImplTest {

    private static final Long OWNER_ID = 10L;
    private static final Long MAIN_ID = 100L;
    private static final String CODE = "tAg1234";
    private static final String CONTACT = "138****0000";
    private static final String CIPHER = "v1:dGVzdElW:dGVzdENpcGhlcnQ=";

    @Mock
    private ToolboxShortCodeService shortCodeService;
    @Mock
    private ToolboxShortCodeMapper shortCodeMapper;
    @Mock
    private ToolboxShortVisitService visitService;
    @Mock
    private ToolboxAssetTagMapper assetTagMapper;
    @Mock
    private ToolboxGcmCipher toolboxGcmCipher;
    @Mock
    private WebBaseUrlResolver webBaseUrlResolver;

    private ToolboxAssetTagServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ToolboxShortCode.class);
        TableInfoHelper.initTableInfo(assistant, ToolboxAssetTag.class);
        TableInfoHelper.initTableInfo(assistant, ToolboxShortVisit.class);
    }

    @BeforeEach
    void setUp() {
        service = new ToolboxAssetTagServiceImpl(shortCodeService, shortCodeMapper, visitService,
                assetTagMapper, toolboxGcmCipher, webBaseUrlResolver);
        when(webBaseUrlResolver.resolve()).thenReturn("https://web.test.local");
        when(shortCodeService.createShortCode(any(), eq(OWNER_ID))).thenAnswer(inv -> {
            AddShortCodeFrom f = inv.getArgument(0);
            return new ShortCodeVo().setId(MAIN_ID).setCode(CODE).setContentType(f.getContentType());
        });
        when(toolboxGcmCipher.encrypt(CONTACT, 1)).thenReturn(CIPHER);
        when(toolboxGcmCipher.decrypt(CIPHER)).thenReturn(CONTACT);
    }

    private ToolboxShortCode mainRow() {
        return new ToolboxShortCode()
                .setId(MAIN_ID)
                .setCode(CODE)
                .setContentType(8)
                .setOwnerUserId(OWNER_ID)
                .setIsPublic(1)
                .setClickCount(3L);
    }

    private ToolboxAssetTag detailRow(int status, String cipher) {
        return new ToolboxAssetTag()
                .setId(1L)
                .setCode(CODE)
                .setItemName("蓝色雨伞")
                .setItemDesc("伞柄有贴纸")
                .setStatus(status)
                .setContactCipher(cipher);
    }

    private CreateAssetTagFrom createFrom(String contact) {
        CreateAssetTagFrom from = new CreateAssetTagFrom();
        from.setItemName("蓝色雨伞");
        from.setContact(contact);
        return from;
    }

    @Test
    @DisplayName("创建标签：主表 type=8 + 联系方式加密落库（库中无明文）+ 状态默认正常")
    void create_encryptsContactAndForcesFields() {
        when(assetTagMapper.insert(any(ToolboxAssetTag.class))).thenReturn(1);

        AssetTagVo vo = service.createAssetTag(createFrom(CONTACT), OWNER_ID);

        ArgumentCaptor<AddShortCodeFrom> shortCaptor = ArgumentCaptor.forClass(AddShortCodeFrom.class);
        verify(shortCodeService).createShortCode(shortCaptor.capture(), eq(OWNER_ID));
        assertEquals(8, shortCaptor.getValue().getContentType(), "主记录复用短码内核 content_type=8");
        assertEquals(1, shortCaptor.getValue().getIsPublic(), "标签恒公开（捡到即可扫）");

        ArgumentCaptor<ToolboxAssetTag> tagCaptor = ArgumentCaptor.forClass(ToolboxAssetTag.class);
        verify(assetTagMapper).insert(tagCaptor.capture());
        assertNotEquals(CONTACT, tagCaptor.getValue().getContactCipher(), "联系方式不得明文落库");
        assertEquals(CIPHER, tagCaptor.getValue().getContactCipher());
        assertEquals(0, tagCaptor.getValue().getStatus(), "新标签默认「正常」");
        assertTrue(vo.isHasContact());
        assertEquals("https://web.test.local/tag/" + CODE, vo.getTagUrl());
    }

    @Test
    @DisplayName("公开解析矩阵：仅「寻物中 + 已留」下发联系方式，正常/已寻回不下发")
    void resolve_contactMatrix() {
        // 寻物中 + 已留 → 解密下发
        when(shortCodeMapper.selectOne(any(Wrapper.class))).thenReturn(mainRow());
        when(assetTagMapper.selectOne(any(Wrapper.class))).thenReturn(detailRow(1, CIPHER));
        AssetTagPublicVo lost = service.resolvePublic(CODE);
        assertTrue(lost.isHasContact());
        assertEquals(CONTACT, lost.getContact());

        // 正常 + 已留 → 不下发（防批量扫码扒信息）
        when(assetTagMapper.selectOne(any(Wrapper.class))).thenReturn(detailRow(0, CIPHER));
        AssetTagPublicVo normal = service.resolvePublic(CODE);
        assertFalse(normal.isHasContact());

        // 已寻回 → 不下发
        when(assetTagMapper.selectOne(any(Wrapper.class))).thenReturn(detailRow(2, CIPHER));
        AssetTagPublicVo found = service.resolvePublic(CODE);
        assertFalse(found.isHasContact());
    }

    @Test
    @DisplayName("公开解析：主表缺失/类型不符/明细悬挂一律 404；成功路径更新最近扫码时间")
    void resolve_missing_goneAndScannedAt() {
        when(shortCodeMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertThrows(CustomExceptions.class, () -> service.resolvePublic(CODE));

        when(shortCodeMapper.selectOne(any(Wrapper.class))).thenReturn(mainRow().setContentType(1));
        assertThrows(CustomExceptions.class, () -> service.resolvePublic(CODE));

        // 成功路径：last_scanned_at 更新被触发（失主线索）
        when(shortCodeMapper.selectOne(any(Wrapper.class))).thenReturn(mainRow());
        when(assetTagMapper.selectOne(any(Wrapper.class))).thenReturn(detailRow(1, CIPHER));
        service.resolvePublic(CODE);
        verify(assetTagMapper).update(eq(null), any(Wrapper.class));
    }

    @Test
    @DisplayName("更新标签：状态裁定在后端；空串 contact = 清空联系方式")
    void update_statusAndClearContact() {
        when(shortCodeMapper.selectById(MAIN_ID)).thenReturn(mainRow());
        when(assetTagMapper.selectOne(any(Wrapper.class))).thenReturn(detailRow(0, CIPHER));
        when(assetTagMapper.updateById(any(ToolboxAssetTag.class))).thenReturn(1);

        UpdateAssetTagFrom from = new UpdateAssetTagFrom();
        from.setStatus(1);
        from.setContact("");
        AssetTagVo vo = service.updateAssetTag(MAIN_ID, from, OWNER_ID);

        assertEquals(1, vo.getStatus(), "寻物状态由后端裁定落库");
        assertFalse(vo.isHasContact(), "空串 contact 应清空密文");
    }

    @Test
    @DisplayName("删除标签：明细 + 访问明细 + 主表级联；归属不符按 404")
    void delete_cascades() {
        when(shortCodeMapper.selectById(MAIN_ID)).thenReturn(mainRow());
        when(assetTagMapper.selectOne(any(Wrapper.class))).thenReturn(detailRow(0, CIPHER));

        service.deleteAssetTag(MAIN_ID, OWNER_ID);

        verify(assetTagMapper).delete(any(Wrapper.class));
        verify(visitService).remove(any(Wrapper.class));
        verify(shortCodeService).removeById(MAIN_ID);

        // 归属不符 → 404 且零删除
        when(shortCodeMapper.selectById(MAIN_ID)).thenReturn(mainRow().setOwnerUserId(99L));
        assertThrows(CustomExceptions.class, () -> service.deleteAssetTag(MAIN_ID, OWNER_ID));
        verify(assetTagMapper, times(1)).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("管理端治理删除：非标签类型 404；级联同构")
    void adminDelete_typeChecked() {
        when(shortCodeMapper.selectById(MAIN_ID)).thenReturn(mainRow().setContentType(1));
        assertThrows(CustomExceptions.class, () -> service.adminDelete(MAIN_ID));

        when(shortCodeMapper.selectById(MAIN_ID)).thenReturn(mainRow());
        when(assetTagMapper.selectOne(any(Wrapper.class))).thenReturn(detailRow(0, CIPHER));
        service.adminDelete(MAIN_ID);
        verify(shortCodeService).removeById(MAIN_ID);
    }

    @Test
    @DisplayName("我的标签分页：主表 owner+type 过滤，明细按 code 批量回填（无 N+1）")
    void page_myTags() {
        Page<ToolboxShortCode> mainPage = new Page<>(1, 10, 1);
        mainPage.setRecords(java.util.List.of(mainRow()));
        when(shortCodeService.page(any(Page.class), any(Wrapper.class))).thenReturn(mainPage);
        when(assetTagMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of(detailRow(1, CIPHER)));

        IPage<AssetTagVo> result = service.pageMyAssetTags(1, 10, OWNER_ID);

        assertEquals(1, result.getRecords().size());
        assertEquals("蓝色雨伞", result.getRecords().get(0).getItemName());
        assertEquals(3L, result.getRecords().get(0).getScanCount());
    }
}
