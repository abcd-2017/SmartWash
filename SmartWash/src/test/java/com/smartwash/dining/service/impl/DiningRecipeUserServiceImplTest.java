package com.smartwash.dining.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.config.MinioConfig;
import com.smartwash.dining.entity.DiningCategory;
import com.smartwash.dining.entity.DiningIngredient;
import com.smartwash.dining.entity.DiningRecipe;
import com.smartwash.dining.entity.DiningRecipeFavorite;
import com.smartwash.dining.entity.DiningRecipeIngredient;
import com.smartwash.dining.from.AddDiningRecipeFrom;
import com.smartwash.dining.from.SearchWebDiningRecipeFrom;
import com.smartwash.dining.mapper.DiningCategoryMapper;
import com.smartwash.dining.mapper.DiningIngredientMapper;
import com.smartwash.dining.mapper.DiningRecipeFavoriteMapper;
import com.smartwash.dining.mapper.DiningRecipeIngredientMapper;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Mock
    private DiningRecipeFavoriteMapper favoriteMapper;

    @Mock
    private DiningRecipeIngredientMapper recipeIngredientMapper;

    @Mock
    private DiningIngredientMapper ingredientMapper;

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
        ReflectionTestUtils.setField(service, "favoriteMapper", favoriteMapper);
        ReflectionTestUtils.setField(service, "recipeIngredientMapper", recipeIngredientMapper);
        ReflectionTestUtils.setField(service, "ingredientMapper", ingredientMapper);
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
        service.userPage(from, 9102L);

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

        Page<DiningRecipeVo> page = service.userPage(new SearchWebDiningRecipeFrom(), 9102L);

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

        DiningRecipeVo vo = service.userDetail(1L, 9102L);

        assertEquals("番茄炒蛋", vo.getName());
        assertEquals("汤羹", vo.getCategoryName());
        assertEquals(List.of("西红柿", "鸡蛋"), vo.getIngredients());
        assertEquals("热锅下油", vo.getSteps());
    }

    @Test
    @DisplayName("详情：下架菜谱拒绝（与不存在同语义，不泄露下架内容）")
    void userDetail_offShelf_rejected() {
        when(recipeMapper.selectById(2L)).thenReturn(recipe(2L, "下架菜", null, DiningRecipe.STATUS_OFF_SHELF));

        expectBusinessException("菜谱不存在或已下架", () -> service.userDetail(2L, 9102L));
    }

    @Test
    @DisplayName("详情：不存在的菜谱拒绝")
    void userDetail_missing_rejected() {
        when(recipeMapper.selectById(404L)).thenReturn(null);

        expectBusinessException("菜谱不存在或已下架", () -> service.userDetail(404L, 9102L));
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

    // ==================== 食材反查搜索（契约 §2.4） ====================

    @Test
    @DisplayName("用户端分页：ingredient 配料名反查与 keyword/categoryId 组合为 AND")
    void userPage_ingredientFilter_combinedWithKeywordAndCategory() {
        when(recipeMapper.selectRecipeIdsByIngredientName("豆腐")).thenReturn(List.of(1L, 2L));
        doReturn(new Page<DiningRecipe>(1, 10, 0)).when(recipeMapper).selectPage(any(), any());

        SearchWebDiningRecipeFrom from = new SearchWebDiningRecipeFrom();
        from.setIngredient("豆腐");
        from.setKeyword("番茄");
        from.setCategoryId(3L);
        service.userPage(from, 9102L);

        verify(recipeMapper).selectRecipeIdsByIngredientName("豆腐");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<DiningRecipe>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(recipeMapper).selectPage(any(), captor.capture());
        String segment = captor.getValue().getSqlSegment().toUpperCase();
        assertTrue(segment.contains("IN"), "反查命中的菜谱 ID 必须以 IN 进入主查询");
        Map<String, Object> params = captor.getValue().getParamNameValuePairs();
        assertTrue(params.containsValue(3L), "categoryId 精确筛选必须进入查询条件");
        assertTrue(params.values().stream().anyMatch(v -> v instanceof String s && s.contains("番茄")),
                "keyword 菜名模糊筛选必须与 ingredient 组合（AND）");
    }

    @Test
    @DisplayName("用户端分页：配料名无命中时短路为空结果（1=0，避免 IN () 语法错误）")
    void userPage_ingredientNoMatch_shortCircuitsEmpty() {
        when(recipeMapper.selectRecipeIdsByIngredientName("不存在的食材")).thenReturn(List.of());
        doReturn(new Page<DiningRecipe>(1, 10, 0)).when(recipeMapper).selectPage(any(), any());

        SearchWebDiningRecipeFrom from = new SearchWebDiningRecipeFrom();
        from.setIngredient("不存在的食材");
        service.userPage(from, 9102L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<DiningRecipe>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(recipeMapper).selectPage(any(), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("1 = 0"), "无命中必须短路为空结果");
    }

    // ==================== favorited 批量填充（契约 §2.3） ====================

    @Test
    @DisplayName("用户端分页：favorited 由一次 IN 查询批量填充，未收藏为 false")
    void userPage_favoritedBatchFilled() {
        Page<DiningRecipe> result = new Page<>(1, 10, 2);
        result.setRecords(List.of(
                recipe(1L, "番茄炒蛋", null, DiningRecipe.STATUS_ON_SHELF),
                recipe(2L, "凉拌黄瓜", null, DiningRecipe.STATUS_ON_SHELF)));
        doReturn(result).when(recipeMapper).selectPage(any(), any());
        when(favoriteMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(new DiningRecipeFavorite().setUserId(9102L).setRecipeId(1L)));

        Page<DiningRecipeVo> page = service.userPage(new SearchWebDiningRecipeFrom(), 9102L);

        assertTrue(page.getRecords().get(0).getFavorited(), "已收藏菜谱 favorited=true");
        assertFalse(page.getRecords().get(1).getFavorited(), "未收藏菜谱 favorited=false");
        verify(favoriteMapper, org.mockito.Mockito.times(1)).selectList(any(Wrapper.class));
    }

    // ==================== 详情过敏原聚合（契约 §2.2） ====================

    @Test
    @DisplayName("详情：allergens 为该菜全部配料过敏原去重集合，按枚举顺序稳定输出")
    void userDetail_allergensAggregatedDistinctInEnumOrder() {
        when(recipeMapper.selectById(1L))
                .thenReturn(recipe(1L, "番茄炒蛋", 2L, DiningRecipe.STATUS_ON_SHELF));
        when(categoryMapper.selectBatchIds(List.of(2L)))
                .thenReturn(List.of(new DiningCategory().setId(2L).setName("汤羹")));
        when(recipeIngredientMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                new DiningRecipeIngredient().setRecipeId(1L).setIngredientId(11L),
                new DiningRecipeIngredient().setRecipeId(1L).setIngredientId(12L),
                new DiningRecipeIngredient().setRecipeId(1L).setIngredientId(13L)));
        when(ingredientMapper.selectBatchIds(List.of(11L, 12L, 13L))).thenReturn(List.of(
                new DiningIngredient().setId(11L).setAllergen(DiningIngredient.ALLERGEN_NUT),
                new DiningIngredient().setId(12L).setAllergen(DiningIngredient.ALLERGEN_EGG),
                new DiningIngredient().setId(13L).setAllergen(DiningIngredient.ALLERGEN_NUT)));
        when(favoriteMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        DiningRecipeVo vo = service.userDetail(1L, 9102L);

        assertEquals(List.of(DiningIngredient.ALLERGEN_NUT, DiningIngredient.ALLERGEN_EGG), vo.getAllergens(),
                "NUT 出现两次只保留一次，且按 ALLERGEN_VALUES 顺序（NUT 在 EGG 前）输出");
    }

    // ==================== 收藏（契约 §2.3） ====================

    @Test
    @DisplayName("收藏：已收藏直接返回当前态（幂等），不重复插入")
    void favorite_alreadyFavorited_idempotent() {
        when(recipeMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(favoriteMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertTrue(service.favorite(9102L, 5L));

        verify(favoriteMapper, never()).insert(any(DiningRecipeFavorite.class));
    }

    @Test
    @DisplayName("收藏：未收藏写入 user_id + recipe_id，返回 true")
    void favorite_new_insertsAndReturnsTrue() {
        when(recipeMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(favoriteMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(favoriteMapper.insert(any(DiningRecipeFavorite.class))).thenReturn(1);

        assertTrue(service.favorite(9102L, 5L));

        ArgumentCaptor<DiningRecipeFavorite> captor = ArgumentCaptor.forClass(DiningRecipeFavorite.class);
        verify(favoriteMapper).insert(captor.capture());
        assertEquals(9102L, captor.getValue().getUserId());
        assertEquals(5L, captor.getValue().getRecipeId());
    }

    @Test
    @DisplayName("收藏：并发撞 uk_user_recipe 唯一键按已收藏处理（幂等）")
    void favorite_duplicateKey_idempotent() {
        when(recipeMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(favoriteMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(favoriteMapper.insert(any(DiningRecipeFavorite.class)))
                .thenThrow(new DuplicateKeyException("uk_user_recipe"));

        assertTrue(service.favorite(9102L, 5L));
    }

    @Test
    @DisplayName("收藏：菜谱不存在时业务失败，不触收藏表")
    void favorite_recipeMissing_rejected() {
        when(recipeMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        expectBusinessException("菜谱不存在", () -> service.favorite(9102L, 5L));

        verify(favoriteMapper, never()).insert(any(DiningRecipeFavorite.class));
    }

    @Test
    @DisplayName("取消收藏：按 user_id + recipe_id 删除，幂等返回 false")
    void unfavorite_deletesAndReturnsFalse() {
        when(favoriteMapper.delete(any(Wrapper.class))).thenReturn(1);

        assertFalse(service.unfavorite(9102L, 5L));

        verify(favoriteMapper).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("我的收藏：按收藏顺序输出，favorited 恒 true，字段同列表")
    void favoritePage_ordersByFavoriteAndMarksFavorited() {
        Page<DiningRecipeFavorite> favPage = new Page<>(1, 10, 2);
        favPage.setRecords(List.of(
                new DiningRecipeFavorite().setRecipeId(1L),
                new DiningRecipeFavorite().setRecipeId(2L)));
        doReturn(favPage).when(favoriteMapper).selectPage(any(), any());
        // 故意乱序返回，服务层须按收藏顺序重排
        when(recipeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                recipe(2L, "凉拌黄瓜", null, DiningRecipe.STATUS_ON_SHELF),
                recipe(1L, "番茄炒蛋", null, DiningRecipe.STATUS_ON_SHELF)));

        Page<DiningRecipeVo> page = service.favoritePage(9102L, new SearchWebDiningRecipeFrom());

        assertEquals(2L, page.getTotal());
        assertEquals(List.of(1L, 2L), page.getRecords().stream()
                .map(DiningRecipeVo::getId).toList(), "必须按收藏顺序（favorite id 倒序）输出");
        assertTrue(page.getRecords().get(0).getFavorited());
        assertTrue(page.getRecords().get(1).getFavorited());
    }

    /** 断言抛出业务异常且消息匹配（CustomExceptions 经全局异常处理转 Result 信封的业务提示） */
    private void expectBusinessException(String expectedMessage, Runnable action) {
        CustomExceptions ex = assertThrows(CustomExceptions.class, action::run);
        assertEquals(expectedMessage, ex.getMessage());
    }
}
