package com.smartwash.dining.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.dining.entity.DiningCategory;
import com.smartwash.dining.entity.DiningRecipe;
import com.smartwash.dining.from.AddDiningRecipeFrom;
import com.smartwash.dining.from.SearchDiningRecipeFrom;
import com.smartwash.dining.from.UpdateDiningRecipeFrom;
import com.smartwash.dining.mapper.DiningCategoryMapper;
import com.smartwash.dining.mapper.DiningRecipeMapper;
import com.smartwash.dining.service.DiningRecipeService;
import com.smartwash.dining.support.RecipeImageValidator;
import com.smartwash.dining.vo.DiningRecipeVo;
import com.smartwash.config.MinioConfig;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.service.FileStorageService;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.SecurityUtil;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 菜谱服务测试（对齐 DiningCategoryServiceImplTest 的纯 Mockito 先例）。
 * 覆盖外部行为契约：管理端分页筛选（分类/状态/关键词）、新增（分类校验/默认上架/来源与创建人落库/
 * 食材 JSON 序列化）、修改（不存在拒绝/分类校验/null 不覆盖）、批量删除、上下架（非法值/不存在拒绝）、
 * 封面上传（JPG/PNG/WebP 白名单 + 魔数 + 扩展名一致性 + 大小上限 + 统一 FileStorageService）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DiningRecipeServiceImpl 管理端行为测试")
class DiningRecipeServiceImplTest {

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
        // SecurityUtil 从 SecurityContextHolder 读取，真实实例即可
        ReflectionTestUtils.setField(service, "securityUtil", new SecurityUtil());
        // MinioConfig.urlOf 为纯字符串拼装，真实实例配占位端点即可
        MinioConfig minioConfig = new MinioConfig();
        minioConfig.setEndpoint("http://minio.local:9000");
        minioConfig.setBucketName("smartwash-test");
        ReflectionTestUtils.setField(service, "minioConfig", minioConfig);
        // 以管理员身份进入（addRecipe 需要记录 created_by_admin_id）
        LoginUser admin = new LoginUser(7L, "ops_admin", "pwd", null, "admin", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private AddDiningRecipeFrom addFrom(String name, Long categoryId) {
        AddDiningRecipeFrom from = new AddDiningRecipeFrom();
        from.setName(name);
        from.setCategoryId(categoryId);
        from.setIngredients(List.of("西红柿", "鸡蛋"));
        from.setSteps("热锅下油");
        return from;
    }

    private DiningRecipe recipe(Long id, String name, Long categoryId, int status) {
        return new DiningRecipe()
                .setId(id)
                .setName(name)
                .setCategoryId(categoryId)
                .setIngredients("[\"西红柿\",\"鸡蛋\"]")
                .setSteps("热锅下油")
                .setSource("ADMIN")
                .setStatus(status);
    }

    // ==================== 新增 ====================

    @Test
    @DisplayName("新增：分类不存在时拒绝，不触库")
    void add_categoryMissing_rejected() {
        when(categoryMapper.selectById(404L)).thenReturn(null);

        expectBusinessException("分类不存在", () -> service.addRecipe(addFrom("番茄炒蛋", 404L)));

        verify(recipeMapper, never()).insert(any(DiningRecipe.class));
    }

    @Test
    @DisplayName("新增：完整映射落库——默认上架、source=ADMIN、创建管理员 ID、食材 List 序列化为 JSON 数组文本")
    void add_success_mapsAllFields() {
        when(categoryMapper.selectById(1L)).thenReturn(new DiningCategory().setId(1L).setName("家常菜"));
        when(recipeMapper.insert(any(DiningRecipe.class))).thenReturn(1);

        service.addRecipe(addFrom("番茄炒蛋", 1L));

        ArgumentCaptor<DiningRecipe> captor = ArgumentCaptor.forClass(DiningRecipe.class);
        verify(recipeMapper).insert(captor.capture());
        DiningRecipe saved = captor.getValue();
        assertEquals("番茄炒蛋", saved.getName());
        assertEquals(1L, saved.getCategoryId());
        assertEquals(DiningRecipe.STATUS_ON_SHELF, saved.getStatus(), "管理端新增默认上架");
        assertEquals("ADMIN", saved.getSource(), "管理端录入来源固定为 ADMIN");
        assertEquals(7L, saved.getCreatedByAdminId(), "创建人管理员 ID 取当前登录上下文");
        assertNull(saved.getCreatedByUserId(), "管理端录入不得误记用户创建人");
        assertEquals("[\"西红柿\",\"鸡蛋\"]", saved.getIngredients(), "食材名数组必须序列化为 JSON 文本落库");
        assertEquals("热锅下油", saved.getSteps());
    }

    @Test
    @DisplayName("新增：分类不传（未分类）落库为 null，不做分类查询")
    void add_withoutCategory_storesNull() {
        when(recipeMapper.insert(any(DiningRecipe.class))).thenReturn(1);

        service.addRecipe(addFrom("凉拌黄瓜", null));

        ArgumentCaptor<DiningRecipe> captor = ArgumentCaptor.forClass(DiningRecipe.class);
        verify(recipeMapper).insert(captor.capture());
        assertNull(captor.getValue().getCategoryId());
        verify(categoryMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("新增：未分类哨兵 0 归一为 null 落库，不做分类查询")
    void add_uncategorizedSentinelZero_normalizedToNull() {
        when(recipeMapper.insert(any(DiningRecipe.class))).thenReturn(1);

        service.addRecipe(addFrom("凉拌黄瓜", 0L));

        ArgumentCaptor<DiningRecipe> captor = ArgumentCaptor.forClass(DiningRecipe.class);
        verify(recipeMapper).insert(captor.capture());
        assertNull(captor.getValue().getCategoryId(), "categoryId=0 必须归一为 null（未分类）");
        verify(categoryMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("修改：categoryId=0 改回未分类（归一为 null 落库）")
    void update_uncategorizedSentinelZero_clearsCategory() {
        when(recipeMapper.selectById(1L)).thenReturn(recipe(1L, "番茄炒蛋", 1L, 1));
        when(recipeMapper.updateById(any(DiningRecipe.class))).thenReturn(1);

        UpdateDiningRecipeFrom from = new UpdateDiningRecipeFrom();
        from.setId(1L);
        from.setCategoryId(0L);
        service.updateRecipe(from);

        ArgumentCaptor<DiningRecipe> captor = ArgumentCaptor.forClass(DiningRecipe.class);
        verify(recipeMapper).updateById(captor.capture());
        assertNull(captor.getValue().getCategoryId(), "categoryId=0 必须把菜谱改回未分类");
    }

    // ==================== 修改 ====================

    @Test
    @DisplayName("修改：菜谱不存在时拒绝")
    void update_missing_rejected() {
        when(recipeMapper.selectById(404L)).thenReturn(null);

        UpdateDiningRecipeFrom from = new UpdateDiningRecipeFrom();
        from.setId(404L);
        from.setName("幽灵菜谱");
        expectBusinessException("菜谱不存在", () -> service.updateRecipe(from));

        verify(recipeMapper, never()).updateById(any(DiningRecipe.class));
    }

    @Test
    @DisplayName("修改：传入分类但分类不存在时拒绝")
    void update_categoryMissing_rejected() {
        when(recipeMapper.selectById(1L)).thenReturn(recipe(1L, "番茄炒蛋", 1L, 1));
        when(categoryMapper.selectById(9L)).thenReturn(null);

        UpdateDiningRecipeFrom from = new UpdateDiningRecipeFrom();
        from.setId(1L);
        from.setCategoryId(9L);
        expectBusinessException("分类不存在", () -> service.updateRecipe(from));

        verify(recipeMapper, never()).updateById(any(DiningRecipe.class));
    }

    @Test
    @DisplayName("修改：仅覆盖传入字段，null 字段保持原值")
    void update_partialFields_appliedAndNullKeeps() {
        when(recipeMapper.selectById(1L)).thenReturn(recipe(1L, "番茄炒蛋", 1L, 1));
        when(categoryMapper.selectById(2L)).thenReturn(new DiningCategory().setId(2L).setName("汤羹"));
        when(recipeMapper.updateById(any(DiningRecipe.class))).thenReturn(1);

        UpdateDiningRecipeFrom from = new UpdateDiningRecipeFrom();
        from.setId(1L);
        from.setName("西红柿炒蛋");
        from.setCategoryId(2L);
        service.updateRecipe(from);

        ArgumentCaptor<DiningRecipe> captor = ArgumentCaptor.forClass(DiningRecipe.class);
        verify(recipeMapper).updateById(captor.capture());
        DiningRecipe updated = captor.getValue();
        assertEquals("西红柿炒蛋", updated.getName(), "传入的 name 必须更新");
        assertEquals(2L, updated.getCategoryId(), "传入的 categoryId 必须更新");
        assertEquals("[\"西红柿\",\"鸡蛋\"]", updated.getIngredients(), "未传入的 ingredients 不得被修改");
        assertEquals("热锅下油", updated.getSteps(), "未传入的 steps 不得被修改");
    }

    @Test
    @DisplayName("修改：食材数组更新时重新序列化为 JSON 文本")
    void update_ingredients_reserialized() {
        when(recipeMapper.selectById(1L)).thenReturn(recipe(1L, "番茄炒蛋", 1L, 1));
        when(recipeMapper.updateById(any(DiningRecipe.class))).thenReturn(1);

        UpdateDiningRecipeFrom from = new UpdateDiningRecipeFrom();
        from.setId(1L);
        from.setIngredients(List.of("西红柿", "鸡蛋", "葱"));
        service.updateRecipe(from);

        ArgumentCaptor<DiningRecipe> captor = ArgumentCaptor.forClass(DiningRecipe.class);
        verify(recipeMapper).updateById(captor.capture());
        assertEquals("[\"西红柿\",\"鸡蛋\",\"葱\"]", captor.getValue().getIngredients());
    }

    // ==================== 删除 ====================

    @Test
    @DisplayName("删除：逗号分隔 id 逐个物理删除")
    void deleteRecipes_splitsIdsAndDeletes() {
        when(recipeMapper.deleteById(anyLong())).thenReturn(1);

        service.deleteRecipes("5,6");

        verify(recipeMapper).deleteById(5L);
        verify(recipeMapper).deleteById(6L);
    }

    @Test
    @DisplayName("删除：畸形分段抛业务提示（防御 NumberFormatException 直达全局兜底）")
    void deleteRecipes_malformedId_rejected() {
        expectBusinessException("ids 必须为逗号分隔的数字", () -> service.deleteRecipes("5,abc"));
    }

    // ==================== 上下架 ====================

    @Test
    @DisplayName("上下架：合法流转（1→0 下架、0→1 上架）按值落库")
    void changeStatus_legalValues_applied() {
        when(recipeMapper.selectById(1L)).thenReturn(recipe(1L, "番茄炒蛋", 1L, 1));
        when(recipeMapper.updateById(any(DiningRecipe.class))).thenReturn(1);

        service.changeStatus(1L, DiningRecipe.STATUS_OFF_SHELF);

        ArgumentCaptor<DiningRecipe> captor = ArgumentCaptor.forClass(DiningRecipe.class);
        verify(recipeMapper).updateById(captor.capture());
        assertEquals(DiningRecipe.STATUS_OFF_SHELF, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("上下架：菜谱不存在时拒绝")
    void changeStatus_missing_rejected() {
        when(recipeMapper.selectById(404L)).thenReturn(null);

        expectBusinessException("菜谱不存在", () -> service.changeStatus(404L, 1));

        verify(recipeMapper, never()).updateById(any(DiningRecipe.class));
    }

    @Test
    @DisplayName("上下架：status 非 0/1 时拒绝（不接受其他取值穿透到库）")
    void changeStatus_illegalValue_rejected() {
        expectBusinessException("status 仅支持 1=上架 0=下架", () -> service.changeStatus(1L, 2));
        expectBusinessException("status 仅支持 1=上架 0=下架", () -> service.changeStatus(1L, null));

        verify(recipeMapper, never()).updateById(any(DiningRecipe.class));
    }

    // ==================== 管理端分页 ====================

    @Test
    @DisplayName("管理端分页：分类/状态/关键词三筛选条件全部进入查询条件")
    void adminPage_allFilters_applied() {
        doReturn(new Page<DiningRecipe>(1, 10, 0)).when(recipeMapper).selectPage(any(), any());

        SearchDiningRecipeFrom from = new SearchDiningRecipeFrom();
        from.setCategoryId(3L);
        from.setStatus(1);
        from.setKeyword("番茄");
        service.adminPage(from);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<DiningRecipe>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(recipeMapper).selectPage(any(), captor.capture());
        captor.getValue().getSqlSegment(); // 物化惰性 SQL 段，条件参数才写入 paramNameValuePairs
        Map<String, Object> params = captor.getValue().getParamNameValuePairs();
        assertTrue(params.containsValue(3L), "categoryId 精确筛选必须进入查询条件");
        assertTrue(params.containsValue(1), "status 精确筛选必须进入查询条件");
        assertTrue(params.values().stream().anyMatch(v -> v instanceof String s && s.contains("番茄")),
                "keyword 菜名模糊筛选必须进入查询条件");
    }

    @Test
    @DisplayName("管理端分页：Vo 映射含分类名联查、封面直链拼装与食材反序列化")
    void adminPage_mapsVoFields() {
        DiningRecipe row = recipe(1L, "番茄炒蛋", 2L, 1)
                .setCoverObjectKey("dining/covers/abc.png")
                .setCreatedByAdminId(7L);
        Page<DiningRecipe> result = new Page<>(1, 10, 1);
        result.setRecords(List.of(row));
        doReturn(result).when(recipeMapper).selectPage(any(), any());
        when(categoryMapper.selectBatchIds(List.of(2L)))
                .thenReturn(List.of(new DiningCategory().setId(2L).setName("汤羹")));

        Page<DiningRecipeVo> page = service.adminPage(new SearchDiningRecipeFrom());

        assertNotNull(page.getRecords());
        DiningRecipeVo vo = page.getRecords().get(0);
        assertEquals("番茄炒蛋", vo.getName());
        assertEquals("汤羹", vo.getCategoryName(), "分类名必须联查回填");
        assertEquals("dining/covers/abc.png", vo.getCoverObjectKey());
        assertNotNull(vo.getCoverUrl(), "封面直链必须由对象键拼装");
        assertTrue(vo.getCoverUrl().contains("dining/covers/abc.png"), "直链必须指向封面对象键");
        assertEquals(List.of("西红柿", "鸡蛋"), vo.getIngredients(), "食材必须由 JSON 文本反序列化为数组");
        assertEquals("ADMIN", vo.getSource());
        assertEquals(1, vo.getStatus());
    }

    // ==================== 封面上传 ====================

    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 13, 'I', 'H', 'D', 'R'};
    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0, 0x10, 'J', 'F', 'I', 'F', 0, 1};
    private static final byte[] WEBP_BYTES = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0,
            'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};
    private static final byte[] HTML_BYTES = {'<', 'h', 't', 'm', 'l', '>', '<', '/', 'h', 't', 'm', 'l', '>'};

    private MockMultipartFile multipart(byte[] content, String filename) {
        return new MockMultipartFile("file", filename, "application/octet-stream", content);
    }

    @Test
    @DisplayName("上传：合法 PNG 通过校验，走统一 FileStorageService 落 dining/covers/ 前缀并返回对象键")
    void uploadCover_validPng_returnsObjectKey() {
        when(fileStorageService.uploadFile(any(), eq("dining/covers"))).thenReturn("dining/covers/x1.png");

        String objectKey = service.uploadCover(multipart(PNG_BYTES, "cover.png"));

        assertEquals("dining/covers/x1.png", objectKey);
        verify(fileStorageService).uploadFile(any(), eq("dining/covers"));
    }

    @Test
    @DisplayName("上传：空文件拒绝")
    void uploadCover_empty_rejected() {
        expectBusinessException("请选择要上传的封面图片",
                () -> service.uploadCover(new MockMultipartFile("file", "a.png", "image/png", new byte[0])));
    }

    @Test
    @DisplayName("上传：超过 5MB 拒绝（413 业务码）")
    void uploadCover_oversize_rejected413() {
        byte[] big = new byte[(int) (RecipeImageValidator.MAX_IMAGE_BYTES + 1)];
        System.arraycopy(PNG_BYTES, 0, big, 0, PNG_BYTES.length);
        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.uploadCover(new MockMultipartFile("file", "a.png", "image/png", big)));
        assertEquals(413, ex.getCode());
        assertEquals("图片大小不能超过5MB", ex.getMessage());
    }

    @Test
    @DisplayName("上传：扩展名白名单外（gif/html/无扩展名）拒绝")
    void uploadCover_disallowedExtension_rejected() {
        expectBusinessException("仅支持 JPG/PNG/WebP 格式图片", () -> service.uploadCover(multipart(PNG_BYTES, "cover.gif")));
        expectBusinessException("仅支持 JPG/PNG/WebP 格式图片", () -> service.uploadCover(multipart(PNG_BYTES, "cover.html")));
        expectBusinessException("仅支持 JPG/PNG/WebP 格式图片", () -> service.uploadCover(multipart(PNG_BYTES, "cover")));
    }

    @Test
    @DisplayName("上传：魔数与扩展名不符拒绝（png 扩展名夹带 JPEG 内容等改后缀攻击）")
    void uploadCover_magicMismatch_rejected() {
        expectBusinessException("图片内容与扩展名不符", () -> service.uploadCover(multipart(JPEG_BYTES, "fake.png")));
        expectBusinessException("图片内容与扩展名不符", () -> service.uploadCover(multipart(PNG_BYTES, "fake.jpg")));
        expectBusinessException("图片内容与扩展名不符", () -> service.uploadCover(multipart(WEBP_BYTES, "fake.png")));
    }

    @Test
    @DisplayName("上传：非图片魔数（HTML/脚本改后缀）拒绝")
    void uploadCover_nonImageContent_rejected() {
        expectBusinessException("仅支持 JPG/PNG/WebP 格式图片", () -> service.uploadCover(multipart(HTML_BYTES, "evil.png")));
        verify(fileStorageService, never()).uploadFile(any(), anyString());
    }

    // ==================== 魔数识别（支撑类直接断言） ====================

    @Test
    @DisplayName("魔数识别：JPEG/PNG/WebP 三类字节头各自命中，乱序/截断不命中")
    void magicRecognition() {
        assertTrue(RecipeImageValidator.isJpeg(JPEG_BYTES));
        assertTrue(RecipeImageValidator.isPng(PNG_BYTES));
        assertTrue(RecipeImageValidator.isWebp(WEBP_BYTES));
        assertFalse(RecipeImageValidator.isPng(JPEG_BYTES));
        assertFalse(RecipeImageValidator.isWebp(PNG_BYTES));
        assertFalse(RecipeImageValidator.isJpeg(WEBP_BYTES));
    }

    /** 断言抛出业务异常且消息匹配（CustomExceptions 经全局异常处理转 Result 信封的业务提示） */
    private void expectBusinessException(String expectedMessage, Runnable action) {
        CustomExceptions ex = assertThrows(CustomExceptions.class, action::run);
        assertEquals(expectedMessage, ex.getMessage());
    }
}
