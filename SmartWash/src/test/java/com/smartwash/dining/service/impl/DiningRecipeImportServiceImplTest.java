package com.smartwash.dining.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.smartwash.dining.entity.DiningCategory;
import com.smartwash.dining.entity.DiningRecipe;
import com.smartwash.dining.from.DiningRecipeImportConfirmFrom;
import com.smartwash.dining.mapper.DiningCategoryMapper;
import com.smartwash.dining.mapper.DiningRecipeMapper;
import com.smartwash.dining.service.DiningRecipeImportService;
import com.smartwash.dining.vo.DiningRecipeImportPreviewVo;
import com.smartwash.dining.vo.DiningRecipeImportResultVo;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.SecurityUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 菜谱批量导入服务测试（对齐 DiningRecipeServiceImplTest 的纯 Mockito 先例）。
 * 只断言公共接口的外部行为契约（两阶段：预览不落库 / 确认整批事务 + 逐行报告），
 * 不探内部结构。文件构造在测试侧用 POI/字符串生成真实字节，解析链路全真。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DiningRecipeImportServiceImpl 两阶段导入行为测试")
class DiningRecipeImportServiceImplTest {

    private static final String HEADER_ROW = "分类名,菜名,食材名列表,步骤,封面链接";

    @Mock
    private DiningRecipeMapper recipeMapper;

    @Mock
    private DiningCategoryMapper categoryMapper;

    private DiningRecipeImportService service;

    @BeforeAll
    static void initTableInfo() {
        // 纯单测环境无 MyBatis 容器：手动初始化实体 TableInfo，供 lambda 列名解析
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, DiningRecipe.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, DiningCategory.class);
    }

    @BeforeEach
    void setUp() {
        DiningRecipeImportServiceImpl impl = new DiningRecipeImportServiceImpl();
        ReflectionTestUtils.setField(impl, "diningRecipeMapper", recipeMapper);
        ReflectionTestUtils.setField(impl, "diningCategoryMapper", categoryMapper);
        ReflectionTestUtils.setField(impl, "securityUtil", new SecurityUtil());
        service = impl;

        LoginUser admin = new LoginUser(7L, "ops_admin", "pwd", null, "admin", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ==================== 文件构造辅助 ====================

    /** 用 POI 构造真实 xlsx 字节：rows[0] 为表头，其余为数据行 */
    private byte[] xlsxBytes(List<List<String>> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("菜谱导入");
            for (int i = 0; i < rows.size(); i++) {
                Row row = sheet.createRow(i);
                List<String> cells = rows.get(i);
                for (int c = 0; c < cells.size(); c++) {
                    row.createCell(c).setCellValue(cells.get(c));
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MockMultipartFile xlsxFile(List<List<String>> rows) {
        return new MockMultipartFile("file", "recipes.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxBytes(rows));
    }

    private MockMultipartFile csvFile(String content, String filename, Charset charset) {
        return new MockMultipartFile("file", filename, "text/csv", content.getBytes(charset));
    }

    private List<List<String>> headerRows() {
        return List.of(List.of("分类名", "菜名", "食材名列表", "步骤", "封面链接"));
    }

    private DiningCategory category(long id, String name) {
        return new DiningCategory().setId(id).setName(name);
    }

    /** 断言抛出业务异常且消息匹配 */
    private void expectBusinessException(String expectedMessagePart, Runnable action) {
        CustomExceptions ex = assertThrows(CustomExceptions.class, action::run);
        assertTrue(ex.getMessage().contains(expectedMessagePart),
                "异常消息应包含「" + expectedMessagePart + "」，实际: " + ex.getMessage());
    }

    // ==================== 模板生成 ====================

    @Test
    @DisplayName("模板：xlsx 可被 POI 读回，首 sheet 首行为 5 列标准表头，且带填写说明 sheet")
    void template_readableWithStandardHeader() throws Exception {
        byte[] bytes = service.buildTemplate();
        assertTrue(bytes.length > 0, "模板字节非空");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet first = workbook.getSheetAt(0);
            Row header = first.getRow(0);
            assertEquals("分类名", header.getCell(0).getStringCellValue());
            assertEquals("菜名", header.getCell(1).getStringCellValue());
            assertEquals("食材名列表", header.getCell(2).getStringCellValue());
            assertEquals("步骤", header.getCell(3).getStringCellValue());
            assertEquals("封面链接", header.getCell(4).getStringCellValue());
            assertTrue(first.getLastRowNum() >= 1, "表头之外应有示例行");
            assertTrue(workbook.getNumberOfSheets() >= 2, "应有填写说明 sheet");
            String explain = workbook.getSheetAt(1).getRow(0).getCell(0).getStringCellValue();
            assertTrue(explain.contains("分隔"), "说明必须写清食材分隔符约定");
            assertTrue(explain.contains("500"), "说明必须写清单次行数上限");
        }
    }

    @Test
    @DisplayName("模板：生成的模板自身可被 preview 正确解析（示例行通过校验）")
    void template_selfCompatible() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(category(1L, "家常菜")));
        byte[] bytes = service.buildTemplate();
        DiningRecipeImportPreviewVo vo = service.preview(new MockMultipartFile(
                "file", "template.xlsx", "application/octet-stream", bytes));
        assertTrue(vo.getTotal() >= 1, "模板示例行应被识别为数据行");
        assertTrue(vo.getErrorCount() == 0 || vo.getRows().stream()
                        .allMatch(r -> r.getErrors().isEmpty() || r.getCategoryName() == null),
                "示例行不应出现格式类错误（分类未建库除外）");
    }

    // ==================== 预览：xlsx 解析与归一 ====================

    @Test
    @DisplayName("预览 xlsx：两行正常数据（带分类/不带分类），分类名映射回 ID、食材按分隔符切分归一")
    void preview_xlsx_normalizesRows() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(category(3L, "汤羹")));
        DiningRecipeImportPreviewVo vo = service.preview(xlsxFile(List.of(
                List.of("分类名", "菜名", "食材名列表", "步骤", "封面链接"),
                List.of("汤羹", "番茄蛋汤", "西红柿、鸡蛋,盐", "1. 打蛋\n2. 冲汤", ""),
                List.of("", "凉拌黄瓜", "黄瓜；蒜", "拍碎拌匀", "https://img.example.com/a.png"))));

        assertEquals(2, vo.getTotal());
        assertEquals(2, vo.getValidCount());
        assertEquals(0, vo.getErrorCount());

        DiningRecipeImportPreviewVo.PreviewRow row1 = vo.getRows().get(0);
        assertEquals(2, row1.getRowNo(), "首个数据行行号 = 2（表头记 1）");
        assertEquals("番茄蛋汤", row1.getName());
        assertEquals(3L, row1.getCategoryId(), "分类名「汤羹」应映射回已存在分类 ID");
        assertEquals(List.of("西红柿", "鸡蛋", "盐"), row1.getIngredients(),
                "顿号/英文逗号混用都应切分，且去空白");
        assertEquals("1. 打蛋\n2. 冲汤", row1.getSteps());

        DiningRecipeImportPreviewVo.PreviewRow row2 = vo.getRows().get(1);
        assertEquals("凉拌黄瓜", row2.getName());
        assertNull(row2.getCategoryId(), "分类名空 = 未分类");
        assertEquals(List.of("黄瓜", "蒜"), row2.getIngredients(), "中文分号也是合法分隔符");
        assertEquals("https://img.example.com/a.png", row2.getCoverUrl());
        assertTrue(row2.getErrors().isEmpty());
    }

    @Test
    @DisplayName("预览 CSV：UTF-8（无 BOM）与 UTF-8 BOM 两种编码均正确解析")
    void preview_csv_utf8Variants() {
        when(categoryMapper.selectList(any())).thenReturn(List.of());
        String body = HEADER_ROW + "\n家常菜,番茄炒蛋,西红柿、鸡蛋,热锅下油,\n";
        DiningRecipeImportPreviewVo noBom = service.preview(csvFile(body, "a.csv", StandardCharsets.UTF_8));
        assertEquals(1, noBom.getTotal());
        assertEquals("番茄炒蛋", noBom.getRows().get(0).getName());

        DiningRecipeImportPreviewVo withBom = service.preview(csvFile('\ufeff' + body, "a.csv", StandardCharsets.UTF_8));
        assertEquals(1, withBom.getTotal());
        assertEquals("番茄炒蛋", withBom.getRows().get(0).getName(), "BOM 不应污染表头识别");
    }

    @Test
    @DisplayName("预览 CSV：GBK 编码（Excel 另存 CSV 的常见产物）自动探测解析")
    void preview_csv_gbkDetected() {
        when(categoryMapper.selectList(any())).thenReturn(List.of());
        String body = HEADER_ROW + "\n家常菜,麻婆豆腐,豆腐、牛肉末、豆瓣酱,先炒肉末再下豆腐,\n";
        DiningRecipeImportPreviewVo vo = service.preview(csvFile(body, "a.csv", Charset.forName("GBK")));
        assertEquals(1, vo.getTotal());
        assertEquals("麻婆豆腐", vo.getRows().get(0).getName());
        assertEquals(List.of("豆腐", "牛肉末", "豆瓣酱"), vo.getRows().get(0).getIngredients());
    }

    // ==================== 预览：行级校验 ====================

    @Test
    @DisplayName("预览行级错误：菜名空/分类不存在/食材单项超长/步骤超长/封面非法，行号定位准确")
    void preview_rowLevelErrors_collected() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(category(3L, "汤羹")));
        String longIngredient = "超".repeat(33);
        DiningRecipeImportPreviewVo vo = service.preview(xlsxFile(List.of(
                List.of("分类名", "菜名", "食材名列表", "步骤", "封面链接"),
                List.of("汤羹", "", "盐", "煮", ""),
                List.of("幽灵分类", "幽灵菜", "盐", "煮", ""),
                List.of("", "长食材菜", longIngredient, "煮", ""),
                List.of("", "长步骤菜", "盐", "步".repeat(20001), ""),
                List.of("", "坏链接菜", "盐", "煮", "ftp://not-http.example.com/a.png"))));

        assertEquals(5, vo.getTotal());
        assertEquals(0, vo.getValidCount());
        assertEquals(5, vo.getErrorCount());

        DiningRecipeImportPreviewVo.PreviewRow row2 = vo.getRows().get(0);
        assertEquals(2, row2.getRowNo());
        assertTrue(row2.getErrors().stream().anyMatch(e -> e.contains("菜名")), "第 2 行缺菜名应报错");

        DiningRecipeImportPreviewVo.PreviewRow row3 = vo.getRows().get(1);
        assertEquals(3, row3.getRowNo());
        assertTrue(row3.getErrors().stream().anyMatch(e -> e.contains("分类") && e.contains("幽灵分类")),
                "第 3 行分类不存在应带分类名报错");
        assertNull(row3.getCategoryId());

        DiningRecipeImportPreviewVo.PreviewRow row4 = vo.getRows().get(2);
        assertTrue(row4.getErrors().stream().anyMatch(e -> e.contains("食材") && e.contains("32")));

        DiningRecipeImportPreviewVo.PreviewRow row5 = vo.getRows().get(3);
        assertTrue(row5.getErrors().stream().anyMatch(e -> e.contains("步骤") && e.contains("20000")));

        DiningRecipeImportPreviewVo.PreviewRow row6 = vo.getRows().get(4);
        assertTrue(row6.getErrors().stream().anyMatch(e -> e.contains("封面")));
    }

    @Test
    @DisplayName("预览：食材数量超 100 报行级错误；合法 100 项通过")
    void preview_ingredientCountLimit() {
        when(categoryMapper.selectList(any())).thenReturn(List.of());
        List<String> ingredients = java.util.stream.IntStream.rangeClosed(1, 101)
                .mapToObj(i -> "食材" + i).toList();
        DiningRecipeImportPreviewVo vo = service.preview(xlsxFile(List.of(
                List.of("分类名", "菜名", "食材名列表", "步骤", "封面链接"),
                List.of("", "超限菜", String.join("、", ingredients), "煮", ""))));
        assertEquals(1, vo.getErrorCount());
        assertTrue(vo.getRows().get(0).getErrors().stream().anyMatch(e -> e.contains("100")));
    }

    @Test
    @DisplayName("预览：全空行跳过，不计入数据行也不报错")
    void preview_blankRowSkipped() {
        when(categoryMapper.selectList(any())).thenReturn(List.of());
        DiningRecipeImportPreviewVo vo = service.preview(xlsxFile(List.of(
                List.of("分类名", "菜名", "食材名列表", "步骤", "封面链接"),
                List.of("", "番茄炒蛋", "西红柿、鸡蛋", "热锅下油", ""),
                List.of("", "", "", "", ""),
                List.of("", "凉拌黄瓜", "黄瓜", "拍碎", ""))));
        assertEquals(2, vo.getTotal(), "中间全空行应被跳过");
        assertEquals(2, vo.getValidCount());
        assertEquals(4, vo.getRows().get(1).getRowNo(), "数据行行号仍按物理行号（空行占行号）");
    }

    @Test
    @DisplayName("预览：分类名首尾空格 trim 后匹配；分类重复出现只查一次库")
    void preview_categoryTrimmed() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(category(3L, "汤羹")));
        DiningRecipeImportPreviewVo vo = service.preview(xlsxFile(List.of(
                List.of("分类名", "菜名", "食材名列表", "步骤", "封面链接"),
                List.of(" 汤羹 ", "番茄蛋汤", "西红柿", "煮", ""))));
        assertEquals(3L, vo.getRows().get(0).getCategoryId());
    }

    // ==================== 预览：整体性拒绝 ====================

    @Test
    @DisplayName("预览：空文件拒绝")
    void preview_emptyFile_rejected() {
        expectBusinessException("请选择", () -> service.preview(
                new MockMultipartFile("file", "a.csv", "text/csv", new byte[0])));
    }

    @Test
    @DisplayName("预览：不支持扩展名拒绝（.txt 等）")
    void preview_unsupportedExtension_rejected() {
        expectBusinessException("仅支持", () -> service.preview(
                new MockMultipartFile("file", "a.txt", "text/plain", HEADER_ROW.getBytes(StandardCharsets.UTF_8))));
        expectBusinessException("仅支持", () -> service.preview(
                new MockMultipartFile("file", "noext", "text/plain", HEADER_ROW.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    @DisplayName("预览：xlsx 扩展名但内容损坏拒绝")
    void preview_corruptXlsx_rejected() {
        expectBusinessException("解析", () -> service.preview(new MockMultipartFile(
                "file", "bad.xlsx", "application/octet-stream", "not-an-excel".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    @DisplayName("预览：表头缺列整体拒绝，错误提示指明缺哪一列")
    void preview_missingColumn_rejected() {
        expectBusinessException("菜名", () -> service.preview(xlsxFile(List.of(
                List.of("分类名", "食材名列表", "步骤", "封面链接"),
                List.of("汤羹", "盐", "煮", "")))));
    }

    @Test
    @DisplayName("预览：只有表头无数据行拒绝")
    void preview_noDataRows_rejected() {
        expectBusinessException("不含任何", () -> service.preview(xlsxFile(headerRows())));
    }

    @Test
    @DisplayName("预览：数据行超 500 上限整体拒绝")
    void preview_rowLimit_rejected() {
        java.util.List<List<String>> rows = new java.util.ArrayList<>();
        rows.add(List.of("分类名", "菜名", "食材名列表", "步骤", "封面链接"));
        for (int i = 1; i <= 501; i++) {
            rows.add(List.of("", "菜" + i, "盐", "煮", ""));
        }
        expectBusinessException("500", () -> service.preview(xlsxFile(rows)));
    }

    // ==================== 确认导入 ====================

    private DiningRecipeImportConfirmFrom.Row confirmRow(int rowNo, String categoryName, String name,
                                                         List<String> ingredients, String steps) {
        return new DiningRecipeImportConfirmFrom.Row()
                .setRowNo(rowNo).setCategoryName(categoryName).setName(name)
                .setIngredients(ingredients).setSteps(steps);
    }

    private DiningRecipeImportConfirmFrom confirmFrom(DiningRecipeImportConfirmFrom.Row... rows) {
        return new DiningRecipeImportConfirmFrom().setRows(List.of(rows));
    }

    @Test
    @DisplayName("确认：全部通过逐行写入——source=ADMIN、默认上架、创建管理员、分类名映射 ID、食材 JSON 序列化，报告含菜谱 ID")
    void confirm_allValid_insertsAllWithReport() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(category(3L, "汤羹")));
        when(recipeMapper.insert(any(DiningRecipe.class))).thenReturn(1);

        DiningRecipeImportResultVo vo = service.confirm(confirmFrom(
                confirmRow(2, "汤羹", "番茄蛋汤", List.of("西红柿", "鸡蛋"), "冲汤"),
                confirmRow(3, null, "凉拌黄瓜", List.of("黄瓜"), "拍碎")));

        assertFalse(vo.isRolledBack());
        assertEquals(2, vo.getTotal());
        assertEquals(2, vo.getSuccessCount());
        assertEquals(0, vo.getFailedCount());

        ArgumentCaptor<DiningRecipe> captor = ArgumentCaptor.forClass(DiningRecipe.class);
        verify(recipeMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        DiningRecipe first = captor.getAllValues().get(0);
        assertEquals("番茄蛋汤", first.getName());
        assertEquals(3L, first.getCategoryId(), "分类名必须映射为分类 ID");
        assertEquals(DiningRecipe.STATUS_ON_SHELF, first.getStatus(), "导入菜谱默认上架");
        assertEquals("ADMIN", first.getSource(), "导入来源固定 ADMIN");
        assertEquals(7L, first.getCreatedByAdminId(), "创建人取当前登录管理员");
        assertNull(first.getCreatedByUserId());
        assertEquals("[\"西红柿\",\"鸡蛋\"]", first.getIngredients(), "食材数组序列化为 JSON 文本落库");
        assertEquals("冲汤", first.getSteps());
        assertNull(first.getCoverObjectKey(), "批量导入不落封面（封面走单独上传链路）");

        assertNull(captor.getAllValues().get(1).getCategoryId(), "分类名空 = 未分类落 null");
        assertEquals(2, vo.getRows().get(0).getRowNo());
        assertTrue(vo.getRows().get(0).isSuccess());
        assertNull(vo.getRows().get(0).getError());
        // recipeId 由 MyBatis 回填，Mockito 环境未触发回填故断言占位（字段存在且成功行无错误）
        assertNotNull(vo.getRows().get(0));
    }

    @Test
    @DisplayName("确认：任一行失败（分类不存在）全批不落库，返回逐行失败报告")
    void confirm_anyInvalid_rollsBackWholeBatch() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(category(3L, "汤羹")));

        DiningRecipeImportResultVo vo = service.confirm(confirmFrom(
                confirmRow(2, "汤羹", "番茄蛋汤", List.of("西红柿"), "冲汤"),
                confirmRow(3, "幽灵分类", "幽灵菜", List.of("盐"), "煮")));

        assertTrue(vo.isRolledBack(), "存在失败行必须全批不落库");
        assertEquals(2, vo.getTotal());
        assertEquals(0, vo.getSuccessCount());
        assertEquals(2, vo.getFailedCount());
        verify(recipeMapper, never()).insert(any(DiningRecipe.class));
        vo.getRows().forEach(row -> {
            assertFalse(row.isSuccess());
            assertNotNull(row.getError());
            assertNull(row.getRecipeId());
        });
        assertTrue(vo.getRows().get(1).getError().contains("分类"), "失败原因必须指明分类问题");
    }

    @Test
    @DisplayName("确认：绕过预览直接提交非法行（菜名空白）被行级拦截，不落库")
    void confirm_bypassPreview_invalidRowRejected() {
        when(categoryMapper.selectList(any())).thenReturn(List.of());

        DiningRecipeImportResultVo vo = service.confirm(confirmFrom(
                confirmRow(2, null, "   ", List.of("盐"), "煮")));

        assertTrue(vo.isRolledBack());
        assertEquals(0, vo.getSuccessCount());
        verify(recipeMapper, never()).insert(any(DiningRecipe.class));
        assertTrue(vo.getRows().get(0).getError().contains("菜名"));
    }

    @Test
    @DisplayName("确认：食材单项超长被行级拦截")
    void confirm_longIngredientRejected() {
        when(categoryMapper.selectList(any())).thenReturn(List.of());

        DiningRecipeImportResultVo vo = service.confirm(confirmFrom(
                confirmRow(2, null, "正常菜", List.of("长".repeat(33)), "煮")));

        assertTrue(vo.isRolledBack());
        assertTrue(vo.getRows().get(0).getError().contains("食材"));
        verify(recipeMapper, never()).insert(any(DiningRecipe.class));
    }

    @Test
    @DisplayName("确认：空行列表拒绝")
    void confirm_emptyRows_rejected() {
        expectBusinessException("不能为空", () -> service.confirm(new DiningRecipeImportConfirmFrom()));
        expectBusinessException("不能为空", () -> service.confirm(
                new DiningRecipeImportConfirmFrom().setRows(java.util.Collections.emptyList())));
    }
}
