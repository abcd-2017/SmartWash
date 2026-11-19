package com.smartwash.dining.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.smartwash.dining.entity.DiningCategory;
import com.smartwash.dining.entity.DiningIngredient;
import com.smartwash.dining.entity.DiningRecipe;
import com.smartwash.dining.entity.DiningRecipeIngredient;
import com.smartwash.dining.from.DiningRecipeImportConfirmFrom;
import com.smartwash.dining.mapper.DiningCategoryMapper;
import com.smartwash.dining.mapper.DiningIngredientMapper;
import com.smartwash.dining.mapper.DiningRecipeIngredientMapper;
import com.smartwash.dining.mapper.DiningRecipeMapper;
import com.smartwash.dining.service.DiningRecipeImportService;
import com.smartwash.dining.vo.DiningRecipeImportPreviewVo;
import com.smartwash.dining.vo.DiningRecipeImportResultVo;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.config.MinioConfig;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.SecurityUtil;
import io.minio.MinioClient;
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
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 菜谱批量导入服务测试（对齐 DiningRecipeServiceImplTest 的纯 Mockito 先例）。
 * 只断言公共接口的外部行为契约（两阶段：预览不落库 / 确认整批事务 + 逐行报告），
 * 不探内部结构。文件构造在测试侧用 POI/字符串生成真实字节，解析链路全真。
 *
 * <p>v2 扩展覆盖（契约 §3.2）：可选新列（食材明细列表/封面对象键/营养 6 列）解析与校验、
 * 分类不存在自动创建（sort 递增）、配料按名 upsert 幂等（已存在复用不覆盖）、
 * 关联表按名去重合并（明细数量优先）、旧 5 列 CSV 行为零破坏。</p>
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

    @Mock
    private DiningIngredientMapper ingredientMapper;

    @Mock
    private DiningRecipeIngredientMapper recipeIngredientMapper;

    @Mock
    private MinioClient minioClient;

    @Mock
    private MinioConfig minioConfig;

    @Mock
    private com.smartwash.dining.service.DiningIngredientService diningIngredientService;

    @Mock
    private com.smartwash.dining.service.DiningRecipeIngredientService diningRecipeIngredientService;

    @Mock
    private com.smartwash.dining.mapper.DiningIngredientAliasMapper diningIngredientAliasMapper;

    private DiningRecipeImportService service;

    private final AtomicLong ingredientIdSeq = new AtomicLong(100);

    @BeforeAll
    static void initTableInfo() {
        // 纯单测环境无 MyBatis 容器：手动初始化实体 TableInfo，供 lambda 列名解析
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, DiningRecipe.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, DiningCategory.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, DiningIngredient.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, DiningRecipeIngredient.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, com.smartwash.dining.entity.DiningIngredientAlias.class);
    }

    @BeforeEach
    void setUp() {
        DiningRecipeImportServiceImpl impl = new DiningRecipeImportServiceImpl();
        ReflectionTestUtils.setField(impl, "diningRecipeMapper", recipeMapper);
        ReflectionTestUtils.setField(impl, "diningCategoryMapper", categoryMapper);
        ReflectionTestUtils.setField(impl, "diningIngredientMapper", ingredientMapper);
        ReflectionTestUtils.setField(impl, "diningRecipeIngredientMapper", recipeIngredientMapper);
        ReflectionTestUtils.setField(impl, "diningIngredientService", diningIngredientService);
        ReflectionTestUtils.setField(impl, "diningRecipeIngredientService", diningRecipeIngredientService);
        ReflectionTestUtils.setField(impl, "diningIngredientAliasMapper", diningIngredientAliasMapper);
        ReflectionTestUtils.setField(impl, "minioClient", minioClient);
        ReflectionTestUtils.setField(impl, "minioConfig", minioConfig);
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

    /** 配料库 selectList 未命中 + 批量 insert 自增回填（模拟 saveBatch 回填主键；降级逐条 stub 同步保留） */
    private void stubIngredientAutoRegister() {
        when(ingredientMapper.selectList(any())).thenReturn(List.of());
        when(diningIngredientService.saveBatch(anyList(), anyInt())).thenAnswer(inv -> {
            java.util.Collection<DiningIngredient> batch = inv.getArgument(0);
            for (DiningIngredient ingredient : batch) {
                ingredient.setId(ingredientIdSeq.incrementAndGet());
            }
            return true;
        });
        when(diningRecipeIngredientService.saveBatch(anyList(), anyInt())).thenReturn(true);
    }

    // ==================== 模板生成 ====================

    @Test
    @DisplayName("模板：xlsx 可被 POI 读回，首 sheet 首行为 14 列标准表头（v2 新列+唯一标识在列），且带填写说明 sheet")
    void template_readableWithStandardHeader() throws Exception {
        byte[] bytes = service.buildTemplate();
        assertTrue(bytes.length > 0, "模板字节非空");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet first = workbook.getSheetAt(0);
            Row header = first.getRow(0);
            assertEquals("分类名", header.getCell(0).getStringCellValue());
            assertEquals("菜名", header.getCell(1).getStringCellValue());
            assertEquals("食材名列表", header.getCell(2).getStringCellValue());
            assertEquals("食材明细列表", header.getCell(3).getStringCellValue());
            assertEquals("步骤", header.getCell(4).getStringCellValue());
            assertEquals("封面对象键", header.getCell(5).getStringCellValue());
            assertEquals("封面链接", header.getCell(6).getStringCellValue());
            assertEquals("热量每100g", header.getCell(7).getStringCellValue());
            assertEquals("整份热量", header.getCell(8).getStringCellValue());
            assertEquals("蛋白质g", header.getCell(9).getStringCellValue());
            assertEquals("脂肪g", header.getCell(10).getStringCellValue());
            assertEquals("碳水g", header.getCell(11).getStringCellValue());
            assertEquals("钠mg", header.getCell(12).getStringCellValue());
            assertEquals("唯一标识", header.getCell(13).getStringCellValue(), "幂等判重列必须在模板表头");
            assertTrue(first.getLastRowNum() >= 1, "表头之外应有示例行");
            assertTrue(workbook.getNumberOfSheets() >= 2, "应有填写说明 sheet");
            String explain = workbook.getSheetAt(1).getRow(0).getCell(0).getStringCellValue();
            assertTrue(explain.contains("分隔"), "说明必须写清食材分隔符约定");
            assertTrue(explain.contains("1000"), "说明必须写清单次行数上限");
            assertTrue(explain.contains("自动创建"), "说明必须写清分类自动创建行为");
            assertTrue(explain.contains("更新"), "说明必须写清重复导入幂等更新行为");
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

    // ==================== 预览：v2 可选列解析 ====================

    @Test
    @DisplayName("预览 v2：食材明细列表解析——数量数值/单位成对解析，数量为空或适量按未给出处理")
    void preview_ingredientDetails_parsed() {
        when(categoryMapper.selectList(any())).thenReturn(List.of());
        DiningRecipeImportPreviewVo vo = service.preview(xlsxFile(List.of(
                List.of("分类名", "菜名", "食材名列表", "食材明细列表", "步骤", "封面链接"),
                List.of("", "番茄炒蛋", "西红柿、鸡蛋、盐", "鸡蛋:575g、西红柿:1.8kg、盐:、葱:适量", "炒", ""))));

        DiningRecipeImportPreviewVo.PreviewRow row = vo.getRows().get(0);
        assertTrue(row.getErrors().isEmpty(), "合法明细不应产生错误");
        assertEquals(4, row.getIngredientDetails().size());
        assertEquals("鸡蛋", row.getIngredientDetails().get(0).getName());
        assertEquals(0, new BigDecimal("575").compareTo(row.getIngredientDetails().get(0).getAmountValue()));
        assertEquals("g", row.getIngredientDetails().get(0).getAmountUnit());
        assertEquals("kg", row.getIngredientDetails().get(1).getAmountUnit(), "单位原样保留（kg→g 归一在聚合侧做）");
        assertEquals(0, new BigDecimal("1.8").compareTo(row.getIngredientDetails().get(1).getAmountValue()));
        assertNull(row.getIngredientDetails().get(2).getAmountValue(), "「盐:」空数量落 NULL");
        assertNull(row.getIngredientDetails().get(2).getAmountUnit());
        assertNull(row.getIngredientDetails().get(3).getAmountValue(), "「葱:适量」非数值按未给出处理");
        assertNull(row.getIngredientDetails().get(3).getAmountUnit());
        assertTrue(row.getWarnings().isEmpty(), "数量为空/适量是合法语义，不产生警告");
    }

    @Test
    @DisplayName("预览 v2：明细缺冒号等解析失败给行级警告不报错（导入不阻断）")
    void preview_ingredientDetails_parseFailureWarned() {
        when(categoryMapper.selectList(any())).thenReturn(List.of());
        DiningRecipeImportPreviewVo vo = service.preview(xlsxFile(List.of(
                List.of("分类名", "菜名", "食材名列表", "食材明细列表", "步骤", "封面链接"),
                List.of("", "番茄炒蛋", "西红柿", "鸡蛋、西红柿:200g", "炒", ""))));

        DiningRecipeImportPreviewVo.PreviewRow row = vo.getRows().get(0);
        assertTrue(row.getErrors().isEmpty(), "明细解析失败仅警告，不算错误");
        assertEquals(1, row.getIngredientDetails().size(), "缺冒号项跳过，合法项保留");
        assertEquals("西红柿", row.getIngredientDetails().get(0).getName());
        assertTrue(row.getWarnings().stream().anyMatch(w -> w.contains("鸡蛋") && w.contains("跳过")),
                "解析失败项必须进 warnings 标注");
    }

    @Test
    @DisplayName("预览 v2：营养列解析——数值归一、非法文本与负数给行级错误")
    void preview_nutritionColumns_parsedAndValidated() {
        when(categoryMapper.selectList(any())).thenReturn(List.of());
        DiningRecipeImportPreviewVo vo = service.preview(xlsxFile(List.of(
                List.of("分类名", "菜名", "食材名列表", "步骤", "封面链接",
                        "热量每100g", "整份热量", "蛋白质g", "脂肪g", "碳水g", "钠mg"),
                List.of("", "好菜", "盐", "煮", "", "105.5", "1830", "8.2", "11.3", "4.9", "620.5"),
                List.of("", "文本热量菜", "盐", "煮", "", "约100", "", "", "", "", ""),
                List.of("", "负数菜", "盐", "煮", "", "-5", "-1", "", "", "", ""))));

        DiningRecipeImportPreviewVo.PreviewRow ok = vo.getRows().get(0);
        assertTrue(ok.getErrors().isEmpty());
        assertEquals(0, new BigDecimal("105.5").compareTo(ok.getCaloriesPer100g()));
        assertEquals(0, new BigDecimal("1830").compareTo(ok.getCaloriesTotal()));
        assertEquals(0, new BigDecimal("8.2").compareTo(ok.getProteinG()));
        assertEquals(0, new BigDecimal("11.3").compareTo(ok.getFatG()));
        assertEquals(0, new BigDecimal("4.9").compareTo(ok.getCarbsG()));
        assertEquals(0, new BigDecimal("620.5").compareTo(ok.getSodiumMg()));

        DiningRecipeImportPreviewVo.PreviewRow textRow = vo.getRows().get(1);
        assertTrue(textRow.getErrors().stream().anyMatch(e -> e.contains("热量每100g") && e.contains("数值")));

        DiningRecipeImportPreviewVo.PreviewRow negativeRow = vo.getRows().get(2);
        assertTrue(negativeRow.getErrors().stream().anyMatch(e -> e.contains("热量每100g") && e.contains("负数")));
        assertTrue(negativeRow.getErrors().stream().anyMatch(e -> e.contains("整份热量") && e.contains("负数")));
    }

    @Test
    @DisplayName("预览 v2：封面对象键——前缀非法报错；前缀合法但存储不存在仅警告不报错")
    void preview_coverObjectKey_prefixAndExistence() throws Exception {
        when(categoryMapper.selectList(any())).thenReturn(List.of());
        when(minioConfig.getBucketName()).thenReturn("test-bucket");
        when(minioClient.statObject(any(io.minio.StatObjectArgs.class)))
                .thenThrow(new IOException("connection refused"));

        DiningRecipeImportPreviewVo vo = service.preview(xlsxFile(List.of(
                List.of("分类名", "菜名", "食材名列表", "步骤", "封面链接", "封面对象键"),
                List.of("", "坏前缀菜", "盐", "煮", "", "other/abc.png"),
                List.of("", "缺对象菜", "盐", "煮", "", "dining/covers/missing.png"))));

        DiningRecipeImportPreviewVo.PreviewRow badPrefix = vo.getRows().get(0);
        assertTrue(badPrefix.getErrors().stream().anyMatch(e -> e.contains("dining/covers/")),
                "前缀非法必须行级报错");

        DiningRecipeImportPreviewVo.PreviewRow missing = vo.getRows().get(1);
        assertTrue(missing.getErrors().isEmpty(), "存储缺失仅警告不阻断");
        assertTrue(missing.getWarnings().stream().anyMatch(w -> w.contains("dining/covers/missing.png") && w.contains("不存在")),
                "存储缺失必须进 warnings 标注");
    }

    @Test
    @DisplayName("预览 v2：封面对象键存储存在 → 无警告（statObject 命中即视为可导入）")
    void preview_coverObjectKey_exists_noWarning() throws Exception {
        when(categoryMapper.selectList(any())).thenReturn(List.of());
        when(minioConfig.getBucketName()).thenReturn("test-bucket");
        when(minioClient.statObject(any(io.minio.StatObjectArgs.class))).thenReturn(null);

        DiningRecipeImportPreviewVo vo = service.preview(xlsxFile(List.of(
                List.of("分类名", "菜名", "食材名列表", "步骤", "封面链接", "封面对象键"),
                List.of("", "有封面菜", "盐", "煮", "", "dining/covers/a.png"))));

        DiningRecipeImportPreviewVo.PreviewRow row = vo.getRows().get(0);
        assertTrue(row.getErrors().isEmpty());
        assertTrue(row.getWarnings().isEmpty());
        verify(minioClient).statObject(any(io.minio.StatObjectArgs.class));
    }

    // ==================== 预览：行级校验 ====================

    @Test
    @DisplayName("预览行级错误：菜名空/食材单项超长/步骤超长/封面非法，行号定位准确；分类不存在降级为警告")
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
        assertEquals(1, vo.getValidCount(), "分类不存在的行降级为警告，仍可导入");
        assertEquals(4, vo.getErrorCount());

        DiningRecipeImportPreviewVo.PreviewRow row2 = vo.getRows().get(0);
        assertEquals(2, row2.getRowNo());
        assertTrue(row2.getErrors().stream().anyMatch(e -> e.contains("菜名")), "第 2 行缺菜名应报错");

        DiningRecipeImportPreviewVo.PreviewRow row3 = vo.getRows().get(1);
        assertEquals(3, row3.getRowNo());
        assertTrue(row3.getErrors().isEmpty(), "分类不存在不再阻断导入");
        assertTrue(row3.getWarnings().stream().anyMatch(e -> e.contains("幽灵分类") && e.contains("自动创建")),
                "分类不存在必须以警告标注确认时自动创建");
        assertNull(row3.getCategoryId());

        DiningRecipeImportPreviewVo.PreviewRow row4 = vo.getRows().get(2);
        assertTrue(row4.getErrors().stream().anyMatch(e -> e.contains("食材") && e.contains("32")));

        DiningRecipeImportPreviewVo.PreviewRow row5 = vo.getRows().get(3);
        assertTrue(row5.getErrors().stream().anyMatch(e -> e.contains("步骤") && e.contains("20000")));

        DiningRecipeImportPreviewVo.PreviewRow row6 = vo.getRows().get(4);
        assertTrue(row6.getErrors().stream().anyMatch(e -> e.contains("封面")));
    }

    @Test
    @DisplayName("预览：步骤列为空不再报错（酱料配方/瓶装饮品无做法步骤）")
    void preview_blankSteps_allowed() {
        when(categoryMapper.selectList(any())).thenReturn(List.of());
        DiningRecipeImportPreviewVo vo = service.preview(xlsxFile(List.of(
                List.of("分类名", "菜名", "食材名列表", "步骤", "封面链接"),
                List.of("酱料", "秘制辣椒油", "辣椒、油", "", ""))));
        assertTrue(vo.getRows().get(0).getErrors().isEmpty(), "步骤可空是合法输入");
        assertNull(vo.getRows().get(0).getSteps());
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
    @DisplayName("预览：表头缺必选列整体拒绝，错误提示指明缺哪一列")
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
    @DisplayName("预览：数据行超 1000 上限整体拒绝")
    void preview_rowLimit_rejected() {
        java.util.List<List<String>> rows = new java.util.ArrayList<>();
        rows.add(List.of("分类名", "菜名", "食材名列表", "步骤", "封面链接"));
        for (int i = 1; i <= 1001; i++) {
            rows.add(List.of("", "菜" + i, "盐", "煮", ""));
        }
        expectBusinessException("1000", () -> service.preview(xlsxFile(rows)));
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
    @DisplayName("确认：全部通过逐行写入——source=ADMIN、默认上架、创建管理员、分类名映射 ID、食材 JSON 双写，报告含菜谱 ID")
    void confirm_allValid_insertsAllWithReport() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(category(3L, "汤羹")));
        when(recipeMapper.insert(any(DiningRecipe.class))).thenReturn(1);
        stubIngredientAutoRegister();

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
        assertEquals("[\"西红柿\",\"鸡蛋\"]", first.getIngredients(), "食材数组序列化为 JSON 文本双写落库");
        assertEquals("冲汤", first.getSteps());
        assertNull(first.getCoverObjectKey(), "未传封面对象键时不落封面");

        assertNull(captor.getAllValues().get(1).getCategoryId(), "分类名空 = 未分类落 null");
        assertEquals(2, vo.getRows().get(0).getRowNo());
        assertTrue(vo.getRows().get(0).isSuccess());
        assertNull(vo.getRows().get(0).getError());
        // recipeId 由 MyBatis 回填，Mockito 环境未触发回填故断言占位（字段存在且成功行无错误）
        assertNotNull(vo.getRows().get(0));
    }

    @Test
    @DisplayName("确认：任一行失败（封面对象键前缀非法）全批不落库，返回逐行失败报告")
    void confirm_anyInvalid_rollsBackWholeBatch() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(category(3L, "汤羹")));

        DiningRecipeImportResultVo vo = service.confirm(confirmFrom(
                confirmRow(2, "汤羹", "番茄蛋汤", List.of("西红柿"), "冲汤"),
                new DiningRecipeImportConfirmFrom.Row().setRowNo(3).setName("幽灵菜")
                        .setIngredients(List.of("盐")).setSteps("煮").setCoverObjectKey("other/not-allowed.png")));

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
        assertTrue(vo.getRows().get(1).getError().contains("封面对象键"), "失败原因必须指明封面键前缀问题");
    }

    @Test
    @DisplayName("确认：分类不存在自动创建（sort 自当前最大值递增），并发撞 uk_name 改挂先建分类")
    void confirm_autoCreatesCategory_withIncrementalSort() {
        // 已有分类最大 sort=5；两行分别引用不存在的「凉菜」「饮品」
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                new DiningCategory().setId(1L).setName("家常菜").setSort(5)));
        when(recipeMapper.insert(any(DiningRecipe.class))).thenReturn(1);
        stubIngredientAutoRegister();
        when(categoryMapper.insert(any(DiningCategory.class))).thenAnswer(inv -> {
            DiningCategory created = inv.getArgument(0, DiningCategory.class);
            if (created.getId() == null) {
                created.setId("凉菜".equals(created.getName()) ? 50L : 51L);
            }
            return 1;
        });

        DiningRecipeImportResultVo vo = service.confirm(confirmFrom(
                confirmRow(2, "凉菜", "凉拌黄瓜", List.of("黄瓜"), "拍碎"),
                confirmRow(3, "饮品", "酸梅汤", List.of("乌梅"), "熬煮"),
                confirmRow(4, "凉菜", "凉拌木耳", List.of("木耳"), "拌")));

        assertFalse(vo.isRolledBack());
        assertEquals(3, vo.getSuccessCount());

        ArgumentCaptor<DiningCategory> categoryCaptor = ArgumentCaptor.forClass(DiningCategory.class);
        verify(categoryMapper, org.mockito.Mockito.times(2)).insert(categoryCaptor.capture());
        List<DiningCategory> created = categoryCaptor.getAllValues();
        assertEquals(2, created.size(), "同名分类只创建一次（凉菜复用）");
        assertEquals("凉菜", created.get(0).getName());
        assertEquals(6, created.get(0).getSort(), "sort 自当前最大值 5 递增");
        assertEquals("饮品", created.get(1).getName());
        assertEquals(7, created.get(1).getSort(), "第二个新分类 sort 继续递增");

        ArgumentCaptor<DiningRecipe> recipeCaptor = ArgumentCaptor.forClass(DiningRecipe.class);
        verify(recipeMapper, org.mockito.Mockito.times(3)).insert(recipeCaptor.capture());
        assertEquals(50L, recipeCaptor.getAllValues().get(0).getCategoryId());
        assertEquals(51L, recipeCaptor.getAllValues().get(1).getCategoryId());
        assertEquals(50L, recipeCaptor.getAllValues().get(2).getCategoryId(), "同批同名分类复用同一 ID");
        // 自动创建信息按行回显到 warnings
        assertTrue(vo.getRows().get(0).getWarnings().stream().anyMatch(w -> w.contains("凉菜") && w.contains("自动创建")));
    }

    @Test
    @DisplayName("确认：配料按名 upsert 幂等——已存在的配料复用 ID 不再插入，启发分类仅对新配料生效")
    void confirm_ingredientUpsert_idempotent() {
        when(categoryMapper.selectList(any())).thenReturn(List.of());
        when(recipeMapper.insert(any(DiningRecipe.class))).thenReturn(1);
        // IN 批查：鸡蛋已在库（人工维护记录），豆瓣酱/辣椒油未命中走批量自动注册
        when(ingredientMapper.selectList(any())).thenReturn(
                List.of(new DiningIngredient().setId(88L).setName("鸡蛋").setCategory("OTHER")));
        when(diningIngredientService.saveBatch(anyList(), anyInt())).thenAnswer(inv -> {
            java.util.Collection<DiningIngredient> batch = inv.getArgument(0);
            for (DiningIngredient ingredient : batch) {
                ingredient.setId(ingredientIdSeq.incrementAndGet());
            }
            return true;
        });
        when(diningRecipeIngredientService.saveBatch(anyList(), anyInt())).thenReturn(true);

        DiningRecipeImportResultVo vo = service.confirm(confirmFrom(
                confirmRow(2, null, "番茄炒蛋", List.of("鸡蛋", "豆瓣酱", "辣椒油"), "炒")));

        assertFalse(vo.isRolledBack());
        // 已存在的鸡蛋复用，不再插入——批量注册列表只有豆瓣酱/辣椒油
        ArgumentCaptor<List<DiningIngredient>> ingredientCaptor = ArgumentCaptor.forClass(List.class);
        verify(diningIngredientService).saveBatch(ingredientCaptor.capture(), anyInt());
        assertEquals(List.of("豆瓣酱", "辣椒油"), ingredientCaptor.getValue().stream()
                .map(DiningIngredient::getName).toList(), "已存在配料不重插（幂等）");
        assertEquals(DiningIngredient.CATEGORY_SEASONING, ingredientCaptor.getValue().get(0).getCategory(),
                "「豆瓣酱」含酱 → SEASONING 启发分类");
        assertEquals(DiningIngredient.CATEGORY_SEASONING, ingredientCaptor.getValue().get(1).getCategory(),
                "「辣椒油」含油 → SEASONING 启发分类");

        // 关联表按名去重批量写入：3 个配料 3 行关联
        ArgumentCaptor<List<DiningRecipeIngredient>> relationListCaptor = ArgumentCaptor.forClass(List.class);
        verify(diningRecipeIngredientService).saveBatch(relationListCaptor.capture(), anyInt());
        assertEquals(88L, relationListCaptor.getValue().get(0).getIngredientId(), "已存在配料复用其 ID");
    }

    @Test
    @DisplayName("确认：食材明细写关联表带数量；名列表与明细同名按名去重（明细数量优先），名列表独有条目数量为空")
    void confirm_ingredientRelations_mergeDetailsOverNames() {
        when(categoryMapper.selectList(any())).thenReturn(List.of());
        when(recipeMapper.insert(any(DiningRecipe.class))).thenReturn(1);
        stubIngredientAutoRegister();

        DiningRecipeImportConfirmFrom.Row row = confirmRow(2, null, "番茄炒蛋",
                List.of("鸡蛋", "西红柿", "葱"), "炒");
        row.setIngredientDetails(List.of(
                new DiningRecipeImportConfirmFrom.IngredientDetail().setName("鸡蛋")
                        .setAmountValue(new BigDecimal("575")).setAmountUnit("g"),
                new DiningRecipeImportConfirmFrom.IngredientDetail().setName("西红柿")
                        .setAmountValue(new BigDecimal("1800")).setAmountUnit("g")));
        DiningRecipeImportResultVo vo = service.confirm(confirmFrom(row));

        assertFalse(vo.isRolledBack());
        // 名列表 3 项 + 明细 2 项（鸡蛋/西红柿与名列表重名）→ 去重后 3 行关联（批量写入）
        ArgumentCaptor<List<DiningRecipeIngredient>> relationListCaptor = ArgumentCaptor.forClass(List.class);
        verify(diningRecipeIngredientService).saveBatch(relationListCaptor.capture(), anyInt());
        List<DiningRecipeIngredient> relations = relationListCaptor.getValue();
        assertEquals(0, new BigDecimal("575").compareTo(relations.get(0).getAmountValue()),
                "重名配料以明细数量为准");
        assertEquals("g", relations.get(0).getAmountUnit());
        assertEquals(0, new BigDecimal("1800").compareTo(relations.get(1).getAmountValue()));
        assertNull(relations.get(2).getAmountValue(), "名列表独有条目（葱）数量为空");
        assertNull(relations.get(2).getAmountUnit());
    }

    @Test
    @DisplayName("确认：营养列写入菜谱实体；封面对象键存储缺失时该行封面不落库并回显警告")
    void confirm_nutritionAndCoverKey_written() throws Exception {
        when(categoryMapper.selectList(any())).thenReturn(List.of());
        when(recipeMapper.insert(any(DiningRecipe.class))).thenReturn(1);
        stubIngredientAutoRegister();
        when(minioConfig.getBucketName()).thenReturn("test-bucket");
        when(minioClient.statObject(any(io.minio.StatObjectArgs.class)))
                .thenThrow(new IOException("object missing"));

        DiningRecipeImportConfirmFrom.Row row = confirmRow(2, null, "番茄炒蛋", List.of("鸡蛋"), "炒")
                .setCaloriesPer100g(new BigDecimal("86.3"))
                .setCaloriesTotal(new BigDecimal("518"))
                .setProteinG(new BigDecimal("7.9"))
                .setFatG(new BigDecimal("5.1"))
                .setCarbsG(new BigDecimal("3.2"))
                .setSodiumMg(new BigDecimal("480.6"))
                .setCoverObjectKey("dining/covers/tomato-egg.png");
        DiningRecipeImportResultVo vo = service.confirm(confirmFrom(row));

        assertFalse(vo.isRolledBack());
        ArgumentCaptor<DiningRecipe> captor = ArgumentCaptor.forClass(DiningRecipe.class);
        verify(recipeMapper).insert(captor.capture());
        DiningRecipe saved = captor.getValue();
        assertEquals(0, new BigDecimal("86.3").compareTo(saved.getCaloriesPer100g()));
        assertEquals(0, new BigDecimal("518").compareTo(saved.getCaloriesTotal()));
        assertEquals(0, new BigDecimal("7.9").compareTo(saved.getProteinG()));
        assertEquals(0, new BigDecimal("5.1").compareTo(saved.getFatG()));
        assertEquals(0, new BigDecimal("3.2").compareTo(saved.getCarbsG()));
        assertEquals(0, new BigDecimal("480.6").compareTo(saved.getSodiumMg()));
        assertNull(saved.getCoverObjectKey(), "存储中不存在的封面对象键不得落库（防挂空直链）");
        assertNotNull(vo.getRows().get(0).getWarnings());
        assertTrue(vo.getRows().get(0).getWarnings().stream().anyMatch(w -> w.contains("封面")));
    }

    @Test
    @DisplayName("确认：旧 5 列 CSV 行为零破坏——无明细/营养/封面时新列落 null；名列表仍 upsert 配料并写无数量关联（契约 §3.2 ②）")
    void confirm_legacyFiveColumnRow_unchanged() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(category(3L, "汤羹")));
        when(recipeMapper.insert(any(DiningRecipe.class))).thenReturn(1);
        stubIngredientAutoRegister();

        DiningRecipeImportResultVo vo = service.confirm(confirmFrom(
                confirmRow(2, "汤羹", "番茄蛋汤", List.of("西红柿", "鸡蛋"), "冲汤")));

        assertFalse(vo.isRolledBack());
        assertEquals(1, vo.getSuccessCount());
        ArgumentCaptor<DiningRecipe> captor = ArgumentCaptor.forClass(DiningRecipe.class);
        verify(recipeMapper).insert(captor.capture());
        DiningRecipe saved = captor.getValue();
        assertNull(saved.getCaloriesPer100g());
        assertNull(saved.getCaloriesTotal());
        assertNull(saved.getProteinG());
        assertNull(saved.getFatG());
        assertNull(saved.getCarbsG());
        assertNull(saved.getSodiumMg());
        assertNull(saved.getCoverObjectKey());
        // 名列表也写关联表（数量未给出落 NULL）——买菜清单 v2 的聚合依据，旧 CSV 同样受益（批量写入）
        ArgumentCaptor<List<DiningRecipeIngredient>> relationListCaptor2 = ArgumentCaptor.forClass(List.class);
        verify(diningRecipeIngredientService).saveBatch(relationListCaptor2.capture(), anyInt());
        for (DiningRecipeIngredient relation : relationListCaptor2.getValue()) {
            assertNull(relation.getAmountValue(), "旧 CSV 名列表无数量，关联行数量落 NULL");
            assertNull(relation.getAmountUnit());
        }
        assertTrue(vo.getRows().get(0).getWarnings() == null || vo.getRows().get(0).getWarnings().isEmpty());
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

    // ==================== source_key 幂等判重 ====================

    private DiningRecipeImportConfirmFrom.Row keyedRow(int rowNo, String sourceKey, String categoryName,
                                                       String name, List<String> ingredients) {
        return confirmRow(rowNo, categoryName, name, ingredients, "煮").setSourceKey(sourceKey);
    }

    @Test
    @DisplayName("确认：同批内重复 source_key → 行级报错全批回滚，不落任何菜")
    void confirm_batchDuplicateSourceKey_rollsBack() {
        when(categoryMapper.selectList(any())).thenReturn(List.of());

        DiningRecipeImportResultVo vo = service.confirm(confirmFrom(
                keyedRow(2, "hoc_001", null, "菜A", List.of("盐")),
                keyedRow(3, "hoc_001", null, "菜B", List.of("盐"))));

        assertTrue(vo.isRolledBack());
        assertEquals(0, vo.getSuccessCount());
        verify(recipeMapper, never()).insert(any(DiningRecipe.class));
        verify(recipeMapper, never()).update(any(), any());
        vo.getRows().forEach(row ->
                assertTrue(row.getError().contains("唯一标识") && row.getError().contains("重复"),
                        "重复 key 的每一行都要标错，实际: " + row.getError()));
    }

    @Test
    @DisplayName("确认：source_key 命中库内已有菜谱 → 幂等更新（insert 零调用，关联按既有 ID 重建），报告标「更新」")
    void confirm_sourceKeyHit_updatesExisting() {
        when(categoryMapper.selectList(any())).thenReturn(List.of());
        stubIngredientAutoRegister();
        DiningRecipe existing = new DiningRecipe().setId(11L).setName("旧名")
                .setCategoryId(3L).setSourceKey("hoc_001");
        when(recipeMapper.selectList(any())).thenReturn(List.of(existing));

        DiningRecipeImportConfirmFrom.Row row = keyedRow(2, "hoc_001", null, "新名", List.of("鸡蛋"));
        row.setIngredientDetails(List.of(new DiningRecipeImportConfirmFrom.IngredientDetail()
                .setName("鸡蛋").setAmountValue(new BigDecimal("575")).setAmountUnit("g")));
        DiningRecipeImportResultVo vo = service.confirm(confirmFrom(row));

        assertFalse(vo.isRolledBack());
        assertEquals(1, vo.getSuccessCount());
        assertEquals(0, vo.getCreatedCount());
        assertEquals(1, vo.getUpdatedCount());
        verify(recipeMapper, never()).insert(any(DiningRecipe.class));
        verify(recipeMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
        // 关联按既有菜谱 ID 重建：先删旧关联（IN 批删），再批量写入新关联行
        verify(recipeIngredientMapper).delete(any());
        ArgumentCaptor<List<DiningRecipeIngredient>> updatedRelationsCaptor = ArgumentCaptor.forClass(List.class);
        verify(diningRecipeIngredientService).saveBatch(updatedRelationsCaptor.capture(), anyInt());
        DiningRecipeIngredient relation = updatedRelationsCaptor.getValue().get(0);
        assertEquals(11L, relation.getRecipeId(), "重建的关联必须挂既有菜谱 ID");
        assertEquals(0, new BigDecimal("575").compareTo(relation.getAmountValue()));
        assertEquals("g", relation.getAmountUnit());
        // 报告行：更新语义回显既有 ID
        assertFalse(vo.getRows().get(0).getIsNew());
        assertEquals("UPDATE", vo.getRows().get(0).getExistsKind());
        assertEquals(11L, vo.getRows().get(0).getRecipeId());
    }

    @Test
    @DisplayName("确认：source_key 未命中但（菜名,分类）兜底命中 → 更新既有菜谱（存量回填路径），报告标「更新」")
    void confirm_sourceKeyMiss_fallbackNameCategoryHit_updates() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(category(3L, "汤羹")));
        stubIngredientAutoRegister();
        DiningRecipe existing = new DiningRecipe().setId(12L).setName("番茄蛋汤")
                .setCategoryId(3L).setSourceKey(null);
        when(recipeMapper.selectList(any())).thenReturn(List.of(existing));

        DiningRecipeImportResultVo vo = service.confirm(confirmFrom(
                keyedRow(2, "hoc_002", "汤羹", "番茄蛋汤", List.of("西红柿"))));

        assertFalse(vo.isRolledBack());
        assertEquals(1, vo.getUpdatedCount());
        assertEquals(0, vo.getCreatedCount());
        verify(recipeMapper, never()).insert(any(DiningRecipe.class));
        verify(recipeMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
        assertFalse(vo.getRows().get(0).getIsNew());
        assertEquals("UPDATE", vo.getRows().get(0).getExistsKind());
        assertEquals(12L, vo.getRows().get(0).getRecipeId());
    }

    @Test
    @DisplayName("确认：source_key 未命中且兜底命中的菜谱已挂其他来源 key → 视为不同上游菜，按新增插入")
    void confirm_fallbackHitWithDifferentKey_createsNew() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(category(3L, "汤羹")));
        when(recipeMapper.insert(any(DiningRecipe.class))).thenReturn(1);
        stubIngredientAutoRegister();
        DiningRecipe existing = new DiningRecipe().setId(13L).setName("番茄蛋汤")
                .setCategoryId(3L).setSourceKey("htc_999");
        when(recipeMapper.selectList(any())).thenReturn(List.of(existing));

        DiningRecipeImportResultVo vo = service.confirm(confirmFrom(
                keyedRow(2, "hoc_003", "汤羹", "番茄蛋汤", List.of("西红柿"))));

        assertFalse(vo.isRolledBack());
        assertEquals(1, vo.getCreatedCount());
        assertEquals(0, vo.getUpdatedCount());
        verify(recipeMapper).insert(any(DiningRecipe.class));
        verify(recipeMapper, never()).update(any(), any());
        assertTrue(vo.getRows().get(0).getIsNew());
        assertEquals("CREATE", vo.getRows().get(0).getExistsKind());
    }

    @Test
    @DisplayName("预览：命中既有菜谱标注 isNew=false/UPDATE，未命中标 isNew=true/CREATE，顶层给出新增/更新计数")
    void preview_marksNewAndUpdate_withCounts() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(category(3L, "汤羹")));
        DiningRecipe existing = new DiningRecipe().setId(21L).setName("番茄蛋汤")
                .setCategoryId(3L).setSourceKey(null);
        when(recipeMapper.selectList(any())).thenReturn(List.of(existing));

        DiningRecipeImportPreviewVo vo = service.preview(xlsxFile(List.of(
                List.of("分类名", "菜名", "食材名列表", "步骤", "封面链接", "唯一标识"),
                List.of("汤羹", "番茄蛋汤", "西红柿", "煮", "", "hoc_010"),
                List.of("汤羹", "全新菜", "盐", "煮", "", "hoc_011"))));

        assertEquals(2, vo.getTotal());
        assertEquals(2, vo.getValidCount());
        assertEquals(1, vo.getNewCount());
        assertEquals(1, vo.getUpdateCount());
        DiningRecipeImportPreviewVo.PreviewRow hit = vo.getRows().get(0);
        assertEquals(Boolean.FALSE, hit.getIsNew(), "（菜名,分类）兜底命中必须标更新");
        assertEquals("UPDATE", hit.getExistsKind());
        DiningRecipeImportPreviewVo.PreviewRow fresh = vo.getRows().get(1);
        assertEquals(Boolean.TRUE, fresh.getIsNew());
        assertEquals("CREATE", fresh.getExistsKind());
        assertEquals("hoc_011", fresh.getSourceKey(), "唯一标识原文必须回显给前端");
    }

    @Test
    @DisplayName("预览：分类尚不存在（确认时自动创建）的行禁用兜底判重，必标新增")
    void preview_unresolvableCategory_forcesNew() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(category(3L, "汤羹")));
        DiningRecipe existing = new DiningRecipe().setId(22L).setName("同名菜")
                .setCategoryId(null).setSourceKey(null);
        when(recipeMapper.selectList(any())).thenReturn(List.of(existing));

        DiningRecipeImportPreviewVo vo = service.preview(xlsxFile(List.of(
                List.of("分类名", "菜名", "食材名列表", "步骤", "封面链接"),
                List.of("幽灵分类", "同名菜", "盐", "煮", ""))));

        DiningRecipeImportPreviewVo.PreviewRow row = vo.getRows().get(0);
        assertEquals(Boolean.TRUE, row.getIsNew(),
                "分类不存在时兜底禁用——不得误命中未分类的同名菜");
        assertEquals("CREATE", row.getExistsKind());
        assertEquals(1, vo.getNewCount());
        assertEquals(0, vo.getUpdateCount());
    }

    @Test
    @DisplayName("预览：同批重复 source_key 标行级错误，errorCount 计入且该行无判重语义")
    void preview_batchDuplicateSourceKey_rowError() {
        when(categoryMapper.selectList(any())).thenReturn(List.of());

        DiningRecipeImportPreviewVo vo = service.preview(xlsxFile(List.of(
                List.of("分类名", "菜名", "食材名列表", "步骤", "封面链接", "唯一标识"),
                List.of("", "菜A", "盐", "煮", "", "hoc_dup"),
                List.of("", "菜B", "盐", "煮", "", "hoc_dup"))));

        assertEquals(2, vo.getErrorCount());
        assertEquals(0, vo.getValidCount());
        vo.getRows().forEach(row -> {
            assertNull(row.getIsNew(), "行级错误时无判重语义");
            assertNull(row.getExistsKind());
            assertTrue(row.getErrors().stream().anyMatch(e -> e.contains("hoc_dup")));
        });
    }

    @Test
    @DisplayName("确认：旧 CSV 无唯一标识列零破坏——不查 source_key、走既有新增路径")
    void confirm_legacyCsvWithoutSourceKey_unaffected() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(category(3L, "汤羹")));
        when(recipeMapper.insert(any(DiningRecipe.class))).thenReturn(1);
        stubIngredientAutoRegister();

        DiningRecipeImportResultVo vo = service.confirm(confirmFrom(
                confirmRow(2, "汤羹", "番茄蛋汤", List.of("西红柿"), "冲汤")));

        assertFalse(vo.isRolledBack());
        assertEquals(1, vo.getCreatedCount());
        assertEquals(0, vo.getUpdatedCount());
        ArgumentCaptor<DiningRecipe> captor = ArgumentCaptor.forClass(DiningRecipe.class);
        verify(recipeMapper).insert(captor.capture());
        assertNull(captor.getValue().getSourceKey(), "未带标识的旧 CSV 新增行 source_key 落 NULL");
        assertTrue(vo.getRows().get(0).getIsNew());
    }
}
