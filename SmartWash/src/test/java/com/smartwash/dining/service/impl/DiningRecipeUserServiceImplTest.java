package com.smartwash.dining.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.config.MinioConfig;
import com.smartwash.dining.entity.DiningCategory;
import com.smartwash.dining.entity.DiningRecipe;
import com.smartwash.dining.from.AddDiningRecipeFrom;
import com.smartwash.dining.from.SearchWebDiningRecipeFrom;
import com.smartwash.dining.mapper.DiningCategoryMapper;
import com.smartwash.dining.mapper.DiningRecipeMapper;
import com.smartwash.dining.vo.DiningRecipeVo;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.service.FileStorageService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 菜谱服务-用户端行为测试（04 号票，纯 Mockito 先例同 DiningRecipeServiceImplTest）。
 * 覆盖外部行为契约：上架列表（status=1 固定过滤 / 分类与关键词筛选 / Vo 映射）、
 * 详情（下架与不存在同语义拒绝）、新增（source=USER / 直上架 / 创建用户落库 / 管理端创建人不误记 /
 * 分类哨兵协议 / 食材序列化）、封面上传复用同一链路。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DiningRecipeServiceImpl 用户端行为测试")
class DiningRecipeUserServiceImplTest {

    @Mock
    private DiningRecipeMapper recipeMapper;

    @Mock
    private DiningCategoryMapper categoryMapper;

    @Mock
    private FileStorageService fileStorageService;

    private DiningRecipeServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // 纯单测环境无 MyBatis 容器：手动初始化实体 TableInfo，供 lambda 列名解析与 wrapper SQL 段物化
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DiningRecipe.class);
        TableInfoHelper.initTableInfo(assistant, DiningCategory.class);
    }

    @BeforeEach
    void setUp() {
        service = new DiningRecipeServiceImpl();
        // ServiceImpl 的内置方法走父类 baseMapper 字段，需手动注入同一 mock
        ReflectionTestUtils.setField(service, "baseMapper", recipeMapper);
        ReflectionTestUtils.setField(service, "diningCategoryMapper", categoryMapper);
        ReflectionTestUtils.setField(service, "fileStorageService", fileStorageService);
        ReflectionTestUtils.setField(service, "securityUtil", new com.smartwash.utils.SecurityUtil());
        // MinioConfig.urlOf 为纯字符串拼装，真实实例配占位端点即可
        MinioConfig minioConfig = new MinioConfig();
        minioConfig.setEndpoint("http://minio.local:9000");
        minioConfig.setBucketName("smartwash-test");
        ReflectionTestUtils.setField(service, "minioConfig", minioConfig);
        // 用户端接口以 userId 方法参数为主体，上下文仅防其他方法误读 null（与生产过滤器同构）
        com.smartwash.utils.LoginUser user = new com.smartwash.utils.LoginUser(
                9102L, "user-13900139001", "pwd", "13900139001", "user", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private DiningRecipe recipe(Long id, String name, Long categoryId, int status) {
        return new DiningRecipe()
                .setId(id)
                .setName(name)
                .setCategoryId(categoryId)
                .setIngredients("[\"西红柿\",\"鸡蛋\"]")
                .setSteps("热锅下油")
                .setSource("USER")
                .setStatus(status);
    }

    // ==================== 上架列表 ====================

    @Test
    @DisplayName("用户端分页：status=1 固定过滤 + 分类/关键词筛选全部进入查询条件")
    void userPage_onShelfFixedAndFilters_applied() {
        doReturn(new Page<DiningRecipe>(1, 10, 0)).when(recipeMapper).selectPage(any(), any());

        SearchWebDiningRecipeFrom from = new SearchWebDiningRecipeFrom();
        from.setCategoryId(3L);
        from.setKeyword("番茄");
        service.userPage(from);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<DiningRecipe>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(recipeMapper).selectPage(any(), captor.capture());
        captor.getValue().getSqlSegment(); // 物化惰性 SQL 段，条件参数才写入 paramNameValuePairs
        Map<String, Object> params = captor.getValue().getParamNameValuePairs();
        assertTrue(params.containsValue(1), "用户端必须固定过滤上架（status=1），下架菜谱对端上不可见");
        assertTrue(params.containsValue(3L), "categoryId 精确筛选必须进入查询条件");
        assertTrue(params.values().stream().anyMatch(v -> v instanceof String s && s.contains("番茄")),
                "keyword 菜名模糊筛选必须进入查询条件");
    }

    @Test
    @DisplayName("用户端分页：Vo 映射含分类名联查、封面直链拼装与食材反序列化")
    void userPage_mapsVoFields() {
        DiningRecipe row = recipe(1L, "番茄炒蛋", 2L, DiningRecipe.STATUS_ON_SHELF)
                .setCoverObjectKey("dining/covers/abc.png");
        Page<DiningRecipe> result = new Page<>(1, 10, 1);
        result.setRecords(List.of(row));
        doReturn(result).when(recipeMapper).selectPage(any(), any());
        when(categoryMapper.selectBatchIds(List.of(2L)))
                .thenReturn(List.of(new DiningCategory().setId(2L).setName("汤羹")));

        Page<DiningRecipeVo> page = service.userPage(new SearchWebDiningRecipeFrom());

        DiningRecipeVo vo = page.getRecords().get(0);
        assertEquals("番茄炒蛋", vo.getName());
        assertEquals("汤羹", vo.getCategoryName(), "分类名必须联查回填");
        assertEquals("dining/covers/abc.png", vo.getCoverObjectKey());
        assertNotNull(vo.getCoverUrl(), "封面直链必须由对象键拼装（MinIO dining/covers 白名单匿名读）");
        assertTrue(vo.getCoverUrl().contains("dining/covers/abc.png"));
        assertEquals(List.of("西红柿", "鸡蛋"), vo.getIngredients(), "食材必须由 JSON 文本反序列化为数组");
        assertEquals(1, vo.getStatus());
    }

    // ==================== 详情 ====================

    @Test
    @DisplayName("详情：上架菜谱返回 Vo（食材数组/步骤/分类名/封面直链）")
    void userDetail_onShelf_returnsVo() {
        when(recipeMapper.selectById(1L)).thenReturn(recipe(1L, "番茄炒蛋", 2L, DiningRecipe.STATUS_ON_SHELF));
        when(categoryMapper.selectBatchIds(List.of(2L)))
                .thenReturn(List.of(new DiningCategory().setId(2L).setName("汤羹")));

        DiningRecipeVo vo = service.userDetail(1L);

        assertEquals("番茄炒蛋", vo.getName());
        assertEquals("汤羹", vo.getCategoryName());
        assertEquals(List.of("西红柿", "鸡蛋"), vo.getIngredients());
        assertEquals("热锅下油", vo.getSteps());
    }

    @Test
    @DisplayName("详情：下架菜谱拒绝（与不存在同语义，不泄露下架内容）")
    void userDetail_offShelf_rejected() {
        when(recipeMapper.selectById(2L)).thenReturn(recipe(2L, "下架菜", null, DiningRecipe.STATUS_OFF_SHELF));

        expectBusinessException("菜谱不存在或已下架", () -> service.userDetail(2L));
    }

    @Test
    @DisplayName("详情：不存在的菜谱拒绝")
    void userDetail_missing_rejected() {
        when(recipeMapper.selectById(404L)).thenReturn(null);

        expectBusinessException("菜谱不存在或已下架", () -> service.userDetail(404L));
    }

    // ==================== 新增 ====================

    private AddDiningRecipeFrom addFrom(String name, Long categoryId) {
        AddDiningRecipeFrom from = new AddDiningRecipeFrom();
        from.setName(name);
        from.setCategoryId(categoryId);
        from.setIngredients(List.of("西红柿", "鸡蛋"));
        from.setSteps("热锅下油");
        return from;
    }

    @Test
    @DisplayName("新增：source=USER 直上架、创建用户落库、管理端创建人不误记、食材序列化 JSON")
    void addUserRecipe_mapsAllFields() {
        when(categoryMapper.selectById(1L)).thenReturn(new DiningCategory().setId(1L).setName("家常菜"));
        when(recipeMapper.insert(any(DiningRecipe.class))).thenReturn(1);

        service.addUserRecipe(addFrom("拿手红烧肉", 1L), 9102L);

        ArgumentCaptor<DiningRecipe> captor = ArgumentCaptor.forClass(DiningRecipe.class);
        verify(recipeMapper).insert(captor.capture());
        DiningRecipe saved = captor.getValue();
        assertEquals("拿手红烧肉", saved.getName());
        assertEquals(1L, saved.getCategoryId());
        assertEquals(DiningRecipe.STATUS_ON_SHELF, saved.getStatus(), "用户端新增直上架（无审核流）");
        assertEquals("USER", saved.getSource(), "用户端录入来源固定为 USER");
        assertEquals(9102L, saved.getCreatedByUserId(), "创建人用户 ID 取登录用户");
        assertNull(saved.getCreatedByAdminId(), "用户端新增不得误记管理员创建人");
        assertEquals("[\"西红柿\",\"鸡蛋\"]", saved.getIngredients(), "食材名数组必须序列化为 JSON 文本落库");
        assertEquals("热锅下油", saved.getSteps());
    }

    @Test
    @DisplayName("新增：分类不存在时拒绝，不触库")
    void addUserRecipe_categoryMissing_rejected() {
        when(categoryMapper.selectById(404L)).thenReturn(null);

        expectBusinessException("分类不存在", () -> service.addUserRecipe(addFrom("幽灵菜", 404L), 9102L));

        verify(recipeMapper, never()).insert(any(DiningRecipe.class));
    }

    @Test
    @DisplayName("新增：未分类哨兵 0 归一为 null 落库，不做分类查询（GLOSSARY 10.10 协议）")
    void addUserRecipe_uncategorizedSentinelZero_normalizedToNull() {
        when(recipeMapper.insert(any(DiningRecipe.class))).thenReturn(1);

        service.addUserRecipe(addFrom("凉拌黄瓜", 0L), 9102L);

        ArgumentCaptor<DiningRecipe> captor = ArgumentCaptor.forClass(DiningRecipe.class);
        verify(recipeMapper).insert(captor.capture());
        assertNull(captor.getValue().getCategoryId(), "categoryId=0 必须归一为 null（未分类）");
        verify(categoryMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("新增：分类不传（null）落库为 null，不做分类查询")
    void addUserRecipe_withoutCategory_storesNull() {
        when(recipeMapper.insert(any(DiningRecipe.class))).thenReturn(1);

        service.addUserRecipe(addFrom("凉拌黄瓜", null), 9102L);

        ArgumentCaptor<DiningRecipe> captor = ArgumentCaptor.forClass(DiningRecipe.class);
        verify(recipeMapper).insert(captor.capture());
        assertNull(captor.getValue().getCategoryId());
        verify(categoryMapper, never()).selectById(anyLong());
    }

    // ==================== 封面上传（复用统一链路） ====================

    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    @Test
    @DisplayName("用户端上传封面：与管理端同校验同存储，落 dining/covers/ 前缀返回对象键")
    void uploadCover_samePipelineAsAdmin() {
        when(fileStorageService.uploadFile(any(), eq("dining/covers"))).thenReturn("dining/covers/u1.png");

        String objectKey = service.uploadCover(new MockMultipartFile(
                "file", "cover.png", "application/octet-stream", PNG_BYTES));

        assertEquals("dining/covers/u1.png", objectKey);
        verify(fileStorageService).uploadFile(any(), eq("dining/covers"));
    }

    @Test
    @DisplayName("用户端上传封面：改后缀攻击同样拒绝（校验链路复用而非放宽）")
    void uploadCover_disguisedContent_rejected() {
        byte[] html = {'<', 'h', 't', 'm', 'l', '>'};
        expectBusinessException("仅支持 JPG/PNG/WebP 格式图片",
                () -> service.uploadCover(new MockMultipartFile("file", "evil.png", "application/octet-stream", html)));
        verify(fileStorageService, never()).uploadFile(any(), eq("dining/covers"));
    }

    /** 断言抛出业务异常且消息匹配（CustomExceptions 经全局异常处理转 Result 信封的业务提示） */
    private void expectBusinessException(String expectedMessage, Runnable action) {
        CustomExceptions ex = assertThrows(CustomExceptions.class, action::run);
        assertEquals(expectedMessage, ex.getMessage());
    }
}
