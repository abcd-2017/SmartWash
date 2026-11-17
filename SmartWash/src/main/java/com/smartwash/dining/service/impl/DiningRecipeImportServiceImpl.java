package com.smartwash.dining.service.impl;

import cn.hutool.core.text.csv.CsvData;
import cn.hutool.core.text.csv.CsvReadConfig;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.text.csv.CsvUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwash.config.MinioConfig;
import com.smartwash.dining.entity.DiningCategory;
import com.smartwash.dining.entity.DiningIngredient;
import com.smartwash.dining.entity.DiningRecipe;
import com.smartwash.dining.entity.DiningRecipeIngredient;
import com.smartwash.dining.from.DiningRecipeImportConfirmFrom;
import com.smartwash.dining.from.DiningRecipeImportConfirmFrom.IngredientDetail;
import com.smartwash.dining.mapper.DiningCategoryMapper;
import com.smartwash.dining.mapper.DiningIngredientMapper;
import com.smartwash.dining.mapper.DiningRecipeIngredientMapper;
import com.smartwash.dining.mapper.DiningRecipeMapper;
import com.smartwash.dining.service.DiningRecipeImportService;
import com.smartwash.dining.vo.DiningRecipeImportPreviewVo;
import com.smartwash.dining.vo.DiningRecipeImportResultVo;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.utils.JsonUtils;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.SecurityUtil;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 菜谱批量导入服务实现（03 号票 + 契约 §3.2 v2 扩展）。
 *
 * <p>两阶段导入：预览（解析 + 逐行校验，不落库）→ 确认（整批事务写入 + 逐行报告）。
 * 事务语义对齐游戏模块关卡批量导入先例：确认阶段再次逐行校验，任一行失败全批不落库，
 * 杜绝半批状态；全部通过才逐行插入。</p>
 *
 * <p>格式支持：.xlsx（POI）与 .csv（Hutool，编码自动探测 UTF-8 BOM / UTF-8 / GBK——
 * Excel 另存 CSV 在中文 Windows 上常为 GBK）。表头按列名匹配（顺序无关），列名：
 * 分类名 / 菜名 / 食材名列表 / 步骤 / 封面链接（既有 5 列，必选）+
 * 食材明细列表 / 封面对象键 / 热量每100g / 整份热量 / 蛋白质g / 脂肪g / 碳水g / 钠mg（v2 全部可选，
 * 旧模板/旧 CSV 不带新列仍可导入）。</p>
 *
 * <p>v2 行为扩展（契约 §3.2）：①分类不存在自动创建（sort 递增，预览按 warning 标注）；
 * ②食材名/明细 upsert dining_ingredient（分类启发预判，已存在的配料不覆盖——避免重导入
 * 冲掉后台人工修正）+ 写 dining_recipe_ingredient 关联（明细优先带数量，名列表补充）；
 * ③营养 6 列写入 recipe；④封面对象键校验 dining/covers/ 前缀 + MinIO statObject 存在性
 * （存储缺失仅行级警告不阻断、该行封面不落库，避免端上挂空直链）。</p>
 *
 * <p>取舍说明：封面链接列仅做格式校验，导入不落库（dining_recipe.cover_object_key 存
 * MinIO 对象键，外部 URL 无法当对象键直链拼装，导入时批量下载外部图片又引入 SSRF 风险）——
 * 封面统一走封面对象键列（v2）或 Web 端单独上传链路。模板为接口动态生成而非静态文件：
 * 列约束与校验规则同源演进，不依赖 jar 内资源路径维护。</p>
 */
@Slf4j
@Service
public class DiningRecipeImportServiceImpl implements DiningRecipeImportService {


    /** 单次导入数据行上限（对齐关卡批量导入 500 上限惯例，防超大请求） */
    public static final int MAX_IMPORT_ROWS = 500;

    /** 上传文件大小上限（5MB，足够承载 500 行文本） */
    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    /** 模板列名（表头按名匹配、顺序无关）——既有 5 列必选，v2 新列全部可选 */
    private static final String COL_CATEGORY = "分类名";
    private static final String COL_NAME = "菜名";
    private static final String COL_INGREDIENTS = "食材名列表";
    private static final String COL_INGREDIENT_DETAILS = "食材明细列表";
    private static final String COL_STEPS = "步骤";
    private static final String COL_COVER_KEY = "封面对象键";
    private static final String COL_COVER = "封面链接";
    private static final String COL_CALORIES_PER_100G = "热量每100g";
    private static final String COL_CALORIES_TOTAL = "整份热量";
    private static final String COL_PROTEIN = "蛋白质g";
    private static final String COL_FAT = "脂肪g";
    private static final String COL_CARBS = "碳水g";
    private static final String COL_SODIUM = "钠mg";

    /** 模板列顺序（导出用；解析按列名匹配，与该顺序无关） */
    private static final String[] TEMPLATE_COLUMNS = {
            COL_CATEGORY, COL_NAME, COL_INGREDIENTS, COL_INGREDIENT_DETAILS, COL_STEPS,
            COL_COVER_KEY, COL_COVER, COL_CALORIES_PER_100G, COL_CALORIES_TOTAL,
            COL_PROTEIN, COL_FAT, COL_CARBS, COL_SODIUM
    };

    /** 食材名分隔符：顿号 / 中文逗号 / 英文逗号 / 中英文分号均兼容（模板填写说明同步声明） */
    private static final Pattern INGREDIENT_DELIMITER = Pattern.compile("[、，,;；]");

    /** 封面链接格式：仅接受 http(s) 外链 */
    private static final Pattern COVER_URL_PATTERN = Pattern.compile("^https?://\\S+$");

    /** 封面对象键命名空间（MinIO 匿名直读白名单前缀，对齐 DiningRecipeServiceImpl.COVER_DIRECTORY） */
    private static final String COVER_KEY_PREFIX = "dining/covers/";

    /** 明细数量格式：数字开头（如 575g / 575 g / 1.5kg），纯文字（适量/若干）不匹配 = 数量未给出 */
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*(.*)$");

    /**
     * 配料分类启发规则（契约 §3.2，按序命中）：
     * 含油盐糖酱醋料酒鸡精味精椒粉汁料→SEASONING；肉蛋鸡牛猪羊鱼虾蟹鸭贝→MAIN；
     * 菜菇笋豆腐瓜茄葱姜蒜→AUX；否则 OTHER。
     */
    private static final Pattern SEASONING_HINT = Pattern.compile("[油盐糖酱醋料酒鸡精味精椒粉汁料]");
    private static final Pattern MAIN_HINT = Pattern.compile("[肉蛋鸡牛猪羊鱼虾蟹鸭贝]");
    private static final Pattern AUX_HINT = Pattern.compile("[菜菇笋豆腐瓜茄葱姜蒜]");

    /** 字段长度上限（与 AddDiningRecipeFrom / DB 列宽约束对齐） */
    private static final int NAME_MAX = 64;
    private static final int CATEGORY_MAX = 32;
    private static final int INGREDIENT_MAX = 32;
    private static final int INGREDIENT_COUNT_MAX = 100;
    private static final int DETAIL_COUNT_MAX = 100;
    private static final int AMOUNT_UNIT_MAX = 16;
    private static final int STEPS_MAX = 20000;
    private static final int COVER_MAX = 500;
    private static final int COVER_KEY_MAX = 255;

    /** 营养列上限（对齐 DB 列精度：DECIMAL(8,2)/(10,2)/(6,2)，防入库截断异常） */
    private static final BigDecimal NUTRITION_MAX_PER_100G = new BigDecimal("999999.99");
    private static final BigDecimal NUTRITION_MAX_TOTAL = new BigDecimal("99999999.99");
    private static final BigDecimal NUTRITION_MAX_NUTRIENT = new BigDecimal("9999.99");

    private static final String SOURCE_ADMIN = "ADMIN";

    /** 菜谱 Mapper：确认阶段逐行写入（insert 后 MyBatis 回填自增 ID 进逐行报告） */
    @Autowired
    private DiningRecipeMapper diningRecipeMapper;

    /** 分类 Mapper：分类名 → ID 映射（一次全量拉取，分类量级小）；确认阶段自动建分类 */
    @Autowired
    private DiningCategoryMapper diningCategoryMapper;

    /** 配料库 Mapper：确认阶段按名 upsert 自动注册 */
    @Autowired
    private DiningIngredientMapper diningIngredientMapper;

    /** 菜谱配料关联 Mapper：确认阶段写聚合主数据源（购买清单 v2） */
    @Autowired
    private DiningRecipeIngredientMapper diningRecipeIngredientMapper;

    /** MinIO 客户端：封面对象键 statObject 存在性校验 */
    @Autowired
    private MinioClient minioClient;

    /** MinIO 配置（桶名） */
    @Autowired
    private MinioConfig minioConfig;

    /** 当前登录上下文（确认导入记录创建管理员） */
    @Autowired
    private SecurityUtil securityUtil;


    @Override
    public byte[] buildTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("菜谱导入");
            Row header = sheet.createRow(0);
            for (int i = 0; i < TEMPLATE_COLUMNS.length; i++) {
                header.createCell(i).setCellValue(TEMPLATE_COLUMNS[i]);
            }
            // 示例行（模板自身可被 preview 正确解析，方便运营对照；v2 新列留空同样合法）
            sheet.createRow(1).createCell(1).setCellValue("番茄炒蛋");
            sheet.getRow(1).createCell(2).setCellValue("西红柿、鸡蛋、葱");
            sheet.getRow(1).createCell(3).setCellValue("鸡蛋:575g、西红柿:1800g、葱:2根");
            sheet.getRow(1).createCell(4).setCellValue("1. 打蛋调匀\n2. 热锅下油炒蛋\n3. 下西红柿翻匀出锅");
            sheet.createRow(2).createCell(1).setCellValue("凉拌黄瓜");
            sheet.getRow(2).createCell(2).setCellValue("黄瓜；蒜");
            sheet.getRow(2).createCell(4).setCellValue("黄瓜拍碎，加蒜拌匀");
            sheet.setColumnWidth(2, 16 * 256);
            sheet.setColumnWidth(3, 32 * 256);
            sheet.setColumnWidth(4, 40 * 256);

            // 填写说明 sheet（首行总述，后续逐列说明）
            Sheet explain = workbook.createSheet("填写说明");
            explain.createRow(0).createCell(0).setCellValue(
                    "填写说明：每行一道菜；食材之间用顿号、逗号或分号分隔；"
                            + "分类不存在将自动创建（也可先在「分类管理」页创建，留空为未分类）；"
                            + "封面链接当前版本仅校验格式不导入（封面请用「封面对象键」列或在导入后于本页单独上传）；"
                            + "单次最多导入 500 行。");
            String[] columnTips = {
                    "分类名（可空=未分类）：不存在将自动创建，也可先在「分类管理」页创建",
                    "菜名（必填）：最长 64 字，允许重名",
                    "食材名列表：食材名之间用顿号、逗号或分号分隔；单个食材名最长 32 字、最多 100 项；只填名称，无需数量",
                    "食材明细列表（可空）：格式 名称:数量单位，顿号分隔（如 鸡蛋:575g、西红柿:1800g）；"
                            + "数量可省略（如 盐: 或 盐:适量，仅建立名字关联）；用于买菜清单按数量聚合",
                    "步骤（可空）：最长 20000 字，换行分段；酱料配方/瓶装饮品可留空",
                    "封面对象键（可空）：MinIO 中 dining/covers/ 前缀的对象键；导入时校验存储存在性，"
                            + "不存在仅警告且该行封面不导入",
                    "封面链接（可空）：仅校验 http(s):// 开头，当前版本不导入，封面请在导入后单独上传",
                    "热量每100g（可空）：Kcal/100g，数值",
                    "整份热量（可空）：整份估算热量 Kcal，数值",
                    "蛋白质g（可空）：g/100g，数值",
                    "脂肪g（可空）：g/100g，数值",
                    "碳水g（可空）：g/100g，数值",
                    "钠mg（可空）：mg/100g，数值",
            };
            for (int i = 0; i < columnTips.length; i++) {
                explain.createRow(i + 1).createCell(0).setCellValue(columnTips[i]);
            }
            explain.setColumnWidth(0, 120 * 256);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("生成菜谱导入模板失败", e);
            throw new CustomExceptions("模板生成失败，请稍后重试");
        }
    }

    // ==================== 阶段一：预览（不落库） ====================

    @Override
    public DiningRecipeImportPreviewVo preview(MultipartFile file) {
        List<List<String>> table = parseUpload(file);
        // 表头缺列 / 无数据行 / 超行数上限：整体拒绝（列级问题给行号没有意义）
        Map<String, Integer> columnMap = resolveHeader(table.get(0));
        List<List<String>> dataRows = table.subList(1, table.size());
        if (dataRows.stream().allMatch(this::isBlankRow)) {
            throw new CustomExceptions("文件不含任何数据行");
        }

        long dataRowCount = dataRows.stream().filter(r -> !isBlankRow(r)).count();
        if (dataRowCount > MAX_IMPORT_ROWS) {
            throw new CustomExceptions("单次最多导入 " + MAX_IMPORT_ROWS + " 行，当前 " + dataRowCount + " 行");
        }
        Map<String, Long> categoryIdByName = loadCategoryIdByName();
        Map<String, Boolean> coverKeyCache = new HashMap<>();
        DiningRecipeImportPreviewVo vo = new DiningRecipeImportPreviewVo()
                .setRows(new ArrayList<>());
        int validCount = 0;
        int errorCount = 0;
        int rowIndex = 1; // 表头占第 1 行
        for (List<String> row : dataRows) {
            rowIndex++;
            if (isBlankRow(row)) {
                continue; // 全空行跳过（行号仍按物理行号递增，与 Excel 打开看到的行号一致）
            }
            ParsedRow parsed = parseTableRow(rowIndex, row, columnMap, coverKeyCache);
            vo.getRows().add(toPreviewRow(parsed, categoryIdByName));
            if (parsed.errors.isEmpty()) {
                validCount++;
            } else {
                errorCount++;
            }
        }
        vo.setTotal(vo.getRows().size());
        vo.setValidCount(validCount);
        vo.setErrorCount(errorCount);
        return vo;
    }

    // ==================== 阶段二：确认（整批事务） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiningRecipeImportResultVo confirm(DiningRecipeImportConfirmFrom from) {
        List<DiningRecipeImportConfirmFrom.Row> rows = from.getRows();
        if (rows == null || rows.isEmpty()) {
            throw new CustomExceptions("导入行列表不能为空");
        }
        if (rows.size() > MAX_IMPORT_ROWS) {
            throw new CustomExceptions("单次最多导入 " + MAX_IMPORT_ROWS + " 行");
        }

        // 确认阶段重新逐行校验（防绕过预览直接提交；预览与确认之间分类/封面存储可能变化）
        Map<String, Boolean> coverKeyCache = new HashMap<>();
        List<ParsedRow> parsedRows = new ArrayList<>(rows.size());
        List<DiningRecipeImportResultVo.ResultRow> reportRows = new ArrayList<>(rows.size());
        boolean anyFailed = false;
        for (DiningRecipeImportConfirmFrom.Row row : rows) {
            ParsedRow parsed = parseConfirmRow(row, coverKeyCache);
            parsedRows.add(parsed);
            DiningRecipeImportResultVo.ResultRow reportRow = new DiningRecipeImportResultVo.ResultRow()
                    .setRowNo(parsed.rowNo).setName(parsed.name);
            if (!parsed.errors.isEmpty()) {
                anyFailed = true;
                reportRow.setSuccess(false).setError(String.join("；", parsed.errors));
            } else {
                reportRow.setSuccess(true);
            }
            reportRow.setWarnings(parsed.warnings.isEmpty() ? null : parsed.warnings);
            reportRows.add(reportRow);
        }

        // 整批事务语义：任一行失败全批不落库，杜绝半批状态（对齐关卡批量导入先例）
        DiningRecipeImportResultVo vo = new DiningRecipeImportResultVo()
                .setTotal(rows.size()).setRows(reportRows);
        if (anyFailed) {
            vo.setRolledBack(true).setSuccessCount(0).setFailedCount(rows.size());
            reportRows.forEach(r -> {
                if (r.isSuccess()) {
                    r.setSuccess(false).setError("同批存在校验失败行，本行未导入");
                }
            });
            return vo;
        }

        // 主数据准备：分类自动创建（sort 递增）→ 配料按名 upsert（本批内缓存，天然幂等）；
        // 两步都可能产生行级警告（自动创建/复用），先同步进逐行报告再落库
        ensureCategories(parsedRows);
        Map<String, Long> ingredientIdByName = ensureIngredients(parsedRows);
        for (int i = 0; i < parsedRows.size(); i++) {
            ParsedRow parsed = parsedRows.get(i);
            reportRows.get(i).setWarnings(parsed.warnings.isEmpty() ? null : parsed.warnings);
        }

        int successCount = 0;
        for (int i = 0; i < parsedRows.size(); i++) {
            ParsedRow parsed = parsedRows.get(i);
            DiningRecipe recipe = buildRecipe(parsed);
            diningRecipeMapper.insert(recipe);
            for (DiningRecipeIngredient relation : buildRelations(recipe.getId(), parsed, ingredientIdByName)) {
                diningRecipeIngredientMapper.insert(relation);
            }
            reportRows.get(i).setRecipeId(recipe.getId());
            successCount++;
        }
        vo.setRolledBack(false).setSuccessCount(successCount).setFailedCount(0);
        log.info("菜谱批量导入完成, 总数: {}, 成功: {}", rows.size(), successCount);
        return vo;
    }

    /** 校验通过 → 构造落库实体（source=ADMIN、默认上架、创建管理员、食材 JSON 双写 + 营养 6 列 + 封面对象键） */
    private DiningRecipe buildRecipe(ParsedRow parsed) {
        DiningRecipe recipe = new DiningRecipe();
        recipe.setName(parsed.name);
        recipe.setCategoryId(parsed.categoryId);
        recipe.setIngredients(parsed.ingredients.isEmpty() ? null : JsonUtils.toJson(parsed.ingredients));
        recipe.setSteps(parsed.steps);
        recipe.setCaloriesPer100g(parsed.caloriesPer100g);
        recipe.setCaloriesTotal(parsed.caloriesTotal);
        recipe.setProteinG(parsed.proteinG);
        recipe.setFatG(parsed.fatG);
        recipe.setCarbsG(parsed.carbsG);
        recipe.setSodiumMg(parsed.sodiumMg);
        // 封面对象键仅在 MinIO 中真实存在才落库（缺失场景已给行级警告），避免端上拿到挂空直链
        recipe.setCoverObjectKey(parsed.coverKeyExists ? parsed.coverObjectKey : null);
        recipe.setSource(SOURCE_ADMIN);
        recipe.setStatus(DiningRecipe.STATUS_ON_SHELF);
        recipe.setCreatedByAdminId(currentAdminId());
        return recipe;
    }

    /**
     * 菜谱配料关联落库集合：按配料名去重保序（uk_recipe_ingredient 一菜一配料一行）。
     * 名列表先占位（无数量），明细后合并：同名配料以明细的数量为准（名列表只承载名字语义）。
     */
    private List<DiningRecipeIngredient> buildRelations(Long recipeId, ParsedRow parsed,
                                                        Map<String, Long> ingredientIdByName) {
        Map<String, DiningRecipeIngredient> byName = new LinkedHashMap<>();
        for (String name : parsed.ingredients) {
            byName.putIfAbsent(name, new DiningRecipeIngredient()
                    .setRecipeId(recipeId)
                    .setIngredientId(ingredientIdByName.get(name)));
        }
        for (IngredientDetail detail : parsed.details) {
            DiningRecipeIngredient existing = byName.get(detail.getName());
            if (existing == null) {
                byName.put(detail.getName(), new DiningRecipeIngredient()
                        .setRecipeId(recipeId)
                        .setIngredientId(ingredientIdByName.get(detail.getName()))
                        .setAmountValue(detail.getAmountValue())
                        .setAmountUnit(detail.getAmountUnit()));
            } else if (existing.getAmountValue() == null && existing.getAmountUnit() == null) {
                existing.setAmountValue(detail.getAmountValue()).setAmountUnit(detail.getAmountUnit());
            }
        }
        return new ArrayList<>(byName.values());
    }

    /**
     * 分类自动创建（契约 §3.2 ①）：不存在即建，sort 自当前最大值递增；
     * 并发窗口撞 uk_name 时改挂先建分类的 id（幂等语义）。
     */
    private void ensureCategories(List<ParsedRow> parsedRows) {
        Map<String, Long> byName = new LinkedHashMap<>();
        int nextSort = 1;
        for (DiningCategory category : diningCategoryMapper.selectList(null)) {
            byName.put(category.getName(), category.getId());
            if (category.getSort() != null && category.getSort() >= nextSort) {
                nextSort = category.getSort() + 1;
            }
        }
        for (ParsedRow parsed : parsedRows) {
            if (parsed.categoryName == null) {
                parsed.categoryId = null;
                continue;
            }
            Long categoryId = byName.get(parsed.categoryName);
            if (categoryId == null) {
                DiningCategory created = new DiningCategory()
                        .setName(parsed.categoryName)
                        .setSort(nextSort++);
                try {
                    diningCategoryMapper.insert(created);
                    categoryId = created.getId();
                    log.info("导入自动创建菜谱分类, name: {}, sort: {}", created.getName(), created.getSort());
                } catch (DuplicateKeyException e) {
                    DiningCategory raced = diningCategoryMapper.selectOne(new LambdaQueryWrapper<DiningCategory>()
                            .eq(DiningCategory::getName, parsed.categoryName));
                    if (raced == null) {
                        throw new CustomExceptions("分类写入冲突，请重试");
                    }
                    categoryId = raced.getId();
                }
                byName.put(parsed.categoryName, categoryId);
                parsed.warnings.add("分类「" + parsed.categoryName + "」不存在，已自动创建");
            }
            parsed.categoryId = categoryId;
        }
    }

    /**
     * 配料按名 upsert（契约 §3.2 ②）：已存在直接复用（不覆盖——避免重导入冲掉后台人工修正的分类），
     * 不存在按启发规则建新；本批内 name→id 缓存，同批重复名只查/建一次（幂等）。
     */
    private Map<String, Long> ensureIngredients(List<ParsedRow> parsedRows) {
        Map<String, Long> cache = new LinkedHashMap<>();
        for (ParsedRow parsed : parsedRows) {
            for (String name : parsed.ingredients) {
                ensureIngredient(name, cache);
            }
            for (IngredientDetail detail : parsed.details) {
                ensureIngredient(detail.getName(), cache);
            }
        }
        return cache;
    }

    private Long ensureIngredient(String name, Map<String, Long> cache) {
        return cache.computeIfAbsent(name, n -> {
            DiningIngredient existing = diningIngredientMapper.selectOne(new LambdaQueryWrapper<DiningIngredient>()
                    .eq(DiningIngredient::getName, n));
            if (existing != null) {
                return existing.getId();
            }
            DiningIngredient ingredient = new DiningIngredient()
                    .setName(n)
                    .setCategory(heuristicCategory(n));
            try {
                diningIngredientMapper.insert(ingredient);
                log.info("导入自动注册配料, name: {}, category: {}", n, ingredient.getCategory());
                return ingredient.getId();
            } catch (DuplicateKeyException e) {
                // 并发窗口撞 uk_name：改挂先建配料的 id（幂等语义）
                DiningIngredient raced = diningIngredientMapper.selectOne(new LambdaQueryWrapper<DiningIngredient>()
                        .eq(DiningIngredient::getName, n));
                if (raced == null) {
                    throw new CustomExceptions("配料写入冲突，请重试");
                }
                return raced.getId();
            }
        });
    }

    /** 配料分类启发（契约 §3.2，按序命中：调料 → 主料 → 辅料 → 其他） */
    private String heuristicCategory(String name) {
        if (SEASONING_HINT.matcher(name).find()) {
            return DiningIngredient.CATEGORY_SEASONING;
        }
        if (MAIN_HINT.matcher(name).find()) {
            return DiningIngredient.CATEGORY_MAIN;
        }
        if (AUX_HINT.matcher(name).find()) {
            return DiningIngredient.CATEGORY_AUX;
        }
        return DiningIngredient.CATEGORY_OTHER;
    }

    // ==================== 行解析（预览读原始单元格 / 确认读结构化入参） ====================

    /** 单行归一结果：预览与确认共用同一套校验，确认阶段的落库数据也取自此对象 */
    private static final class ParsedRow {
        private Integer rowNo;
        private String categoryName;
        private Long categoryId;
        private String name;
        private List<String> ingredients = List.of();
        private List<IngredientDetail> details = List.of();
        private String steps;
        private String coverUrl;
        private String coverObjectKey;
        private boolean coverKeyExists;
        private BigDecimal caloriesPer100g;
        private BigDecimal caloriesTotal;
        private BigDecimal proteinG;
        private BigDecimal fatG;
        private BigDecimal carbsG;
        private BigDecimal sodiumMg;
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
    }

    /** 预览：原始表格行 → 归一结果（含明细解析、营养数值解析、行级警告） */
    private ParsedRow parseTableRow(int rowNo, List<String> row, Map<String, Integer> columnMap,
                                    Map<String, Boolean> coverKeyCache) {
        ParsedRow parsed = new ParsedRow();
        parsed.rowNo = rowNo;
        parsed.categoryName = trimToNull(cell(row, columnMap.get(COL_CATEGORY)));
        parsed.name = trimToNull(cell(row, columnMap.get(COL_NAME)));
        String ingredientsRaw = cell(row, columnMap.get(COL_INGREDIENTS));
        parsed.ingredients = StringUtils.hasText(ingredientsRaw)
                ? Arrays.stream(INGREDIENT_DELIMITER.split(ingredientsRaw))
                .map(String::trim).filter(s -> !s.isEmpty()).toList()
                : List.of();
        parsed.details = parseDetailItems(cell(row, columnMap.get(COL_INGREDIENT_DETAILS)), parsed.warnings);
        parsed.steps = trimToNull(cell(row, columnMap.get(COL_STEPS)));
        parsed.coverUrl = trimToNull(cell(row, columnMap.get(COL_COVER)));
        parsed.coverObjectKey = trimToNull(cell(row, columnMap.get(COL_COVER_KEY)));
        parsed.caloriesPer100g = parseNutrition(COL_CALORIES_PER_100G,
                cell(row, columnMap.get(COL_CALORIES_PER_100G)), parsed.errors);
        parsed.caloriesTotal = parseNutrition(COL_CALORIES_TOTAL,
                cell(row, columnMap.get(COL_CALORIES_TOTAL)), parsed.errors);
        parsed.proteinG = parseNutrition(COL_PROTEIN, cell(row, columnMap.get(COL_PROTEIN)), parsed.errors);
        parsed.fatG = parseNutrition(COL_FAT, cell(row, columnMap.get(COL_FAT)), parsed.errors);
        parsed.carbsG = parseNutrition(COL_CARBS, cell(row, columnMap.get(COL_CARBS)), parsed.errors);
        parsed.sodiumMg = parseNutrition(COL_SODIUM, cell(row, columnMap.get(COL_SODIUM)), parsed.errors);
        validateParsed(parsed, coverKeyCache);
        return parsed;
    }

    /** 确认：结构化入参 → 归一结果（再次全量校验，防绕过预览直接提交） */
    private ParsedRow parseConfirmRow(DiningRecipeImportConfirmFrom.Row row, Map<String, Boolean> coverKeyCache) {
        ParsedRow parsed = new ParsedRow();
        parsed.rowNo = row.getRowNo();
        parsed.categoryName = trimToNull(row.getCategoryName());
        parsed.name = trimToNull(row.getName());
        parsed.ingredients = row.getIngredients() == null ? List.of()
                : row.getIngredients().stream()
                .filter(StringUtils::hasText).map(String::trim).toList();
        parsed.details = row.getIngredientDetails() == null ? List.of()
                : row.getIngredientDetails().stream()
                .map(d -> new IngredientDetail()
                        .setName(d.getName() == null ? null : d.getName().trim())
                        .setAmountValue(d.getAmountValue())
                        .setAmountUnit(trimToNull(d.getAmountUnit())))
                .toList();
        parsed.steps = trimToNull(row.getSteps());
        parsed.coverObjectKey = trimToNull(row.getCoverObjectKey());
        parsed.caloriesPer100g = row.getCaloriesPer100g();
        parsed.caloriesTotal = row.getCaloriesTotal();
        parsed.proteinG = row.getProteinG();
        parsed.fatG = row.getFatG();
        parsed.carbsG = row.getCarbsG();
        parsed.sodiumMg = row.getSodiumMg();
        validateParsed(parsed, coverKeyCache);
        return parsed;
    }

    /** 归一结果 → 预览行（分类 id 只回填已存在的；不存在的分类以 warning 标注确认时自动创建） */
    private DiningRecipeImportPreviewVo.PreviewRow toPreviewRow(ParsedRow parsed,
                                                                Map<String, Long> categoryIdByName) {
        Long categoryId = parsed.categoryName == null ? null : categoryIdByName.get(parsed.categoryName);
        if (parsed.categoryName != null && categoryId == null) {
            parsed.warnings.add("分类「" + parsed.categoryName + "」不存在，确认导入时将自动创建");
        }
        return new DiningRecipeImportPreviewVo.PreviewRow()
                .setRowNo(parsed.rowNo)
                .setCategoryName(parsed.categoryName)
                .setCategoryId(categoryId)
                .setName(parsed.name)
                .setIngredients(parsed.ingredients)
                .setIngredientDetails(parsed.details.stream()
                        .map(d -> new DiningRecipeImportPreviewVo.IngredientDetail()
                                .setName(d.getName()).setAmountValue(d.getAmountValue()).setAmountUnit(d.getAmountUnit()))
                        .toList())
                .setSteps(parsed.steps)
                .setCoverUrl(parsed.coverUrl)
                .setCoverObjectKey(parsed.coverObjectKey)
                .setCaloriesPer100g(parsed.caloriesPer100g)
                .setCaloriesTotal(parsed.caloriesTotal)
                .setProteinG(parsed.proteinG)
                .setFatG(parsed.fatG)
                .setCarbsG(parsed.carbsG)
                .setSodiumMg(parsed.sodiumMg)
                .setErrors(parsed.errors)
                .setWarnings(parsed.warnings);
    }

    /**
     * 明细解析（契约 §3.2：`名称:数量单位`，分隔符与食材名列表同套；数量可为空）。
     * 冒号（中英文）后为空或非数值（适量/若干）→ 数量未给出，仅建名字关联；
     * 无冒号 / 配料名为空 → 明细解析失败，行级警告并跳过该项（契约 §3.2 ④）。
     */
    private List<IngredientDetail> parseDetailItems(String raw, List<String> warnings) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        List<IngredientDetail> details = new ArrayList<>();
        for (String item : INGREDIENT_DELIMITER.split(raw)) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separator = indexOfNameAmountSeparator(trimmed);
            if (separator < 0) {
                warnings.add("食材明细「" + trimmed + "」缺少「名称:数量」冒号分隔，已跳过");
                continue;
            }
            String name = trimmed.substring(0, separator).trim();
            if (name.isEmpty()) {
                warnings.add("食材明细「" + trimmed + "」配料名为空，已跳过");
                continue;
            }
            BigDecimal amountValue = null;
            String amountUnit = null;
            String amountPart = trimmed.substring(separator + 1).trim();
            if (!amountPart.isEmpty()) {
                Matcher matcher = AMOUNT_PATTERN.matcher(amountPart);
                if (matcher.matches()) {
                    amountValue = new BigDecimal(matcher.group(1));
                    amountUnit = matcher.group(2).isEmpty() ? null : matcher.group(2).trim();
                }
            }
            details.add(new IngredientDetail().setName(name).setAmountValue(amountValue).setAmountUnit(amountUnit));
        }
        return details;
    }

    /** 名称与数量的分隔符下标（兼容中文冒号，取最先出现者） */
    private static int indexOfNameAmountSeparator(String text) {
        int ascii = text.indexOf(':');
        int fullWidth = text.indexOf('：');
        if (ascii < 0) {
            return fullWidth;
        }
        if (fullWidth < 0) {
            return ascii;
        }
        return Math.min(ascii, fullWidth);
    }

    /** 营养单元格文本 → BigDecimal（空白=未填；非法数值记行级错误） */
    private BigDecimal parseNutrition(String label, String text, List<String> errors) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            errors.add(label + "必须为数值");
            return null;
        }
    }

    // ==================== 共用校验（预览与确认同一套规则） ====================

    /**
     * 行级校验：错误追加进 errors（空列表 = 通过，任一错误确认导入即全批回滚）；
     * 警告追加进 warnings（不阻断：分类自动创建/封面对象键存储缺失/明细解析失败）。
     * 步骤仅校验长度上限（酱料配方/瓶装饮品可留空，契约 §3.2）。
     */
    private void validateParsed(ParsedRow parsed, Map<String, Boolean> coverKeyCache) {
        if (!StringUtils.hasText(parsed.name)) {
            parsed.errors.add("菜名不能为空");
        } else if (parsed.name.length() > NAME_MAX) {
            parsed.errors.add("菜名过长（上限" + NAME_MAX + "字符）");
        }
        if (parsed.categoryName != null && parsed.categoryName.length() > CATEGORY_MAX) {
            parsed.errors.add("分类名过长（上限" + CATEGORY_MAX + "字符）");
        }
        if (parsed.ingredients.size() > INGREDIENT_COUNT_MAX) {
            parsed.errors.add("食材数量过多（上限" + INGREDIENT_COUNT_MAX + "项）");
        }
        for (String ingredient : parsed.ingredients) {
            if (ingredient.length() > INGREDIENT_MAX) {
                parsed.errors.add("食材名「" + ingredient + "」过长（上限" + INGREDIENT_MAX + "字符）");
                break; // 同类错误报一次即可，避免报告刷屏
            }
        }
        if (parsed.details.size() > DETAIL_COUNT_MAX) {
            parsed.errors.add("食材明细数量过多（上限" + DETAIL_COUNT_MAX + "项）");
        }
        for (IngredientDetail detail : parsed.details) {
            if (detail.getName() == null || detail.getName().length() > INGREDIENT_MAX) {
                parsed.errors.add("食材明细配料名过长（上限" + INGREDIENT_MAX + "字符）");
                break;
            }
            if (detail.getAmountUnit() != null && detail.getAmountUnit().length() > AMOUNT_UNIT_MAX) {
                parsed.errors.add("食材明细「" + detail.getName() + "」单位过长（上限" + AMOUNT_UNIT_MAX + "字符）");
                break;
            }
            if (detail.getAmountValue() != null && detail.getAmountValue().signum() < 0) {
                parsed.errors.add("食材明细「" + detail.getName() + "」数量不能为负数");
                break;
            }
        }
        if (StringUtils.hasText(parsed.steps) && parsed.steps.length() > STEPS_MAX) {
            parsed.errors.add("步骤内容过长（上限" + STEPS_MAX + "字符）");
        }
        if (StringUtils.hasText(parsed.coverUrl)) {
            if (parsed.coverUrl.length() > COVER_MAX) {
                parsed.errors.add("封面链接过长（上限" + COVER_MAX + "字符）");
            } else if (!COVER_URL_PATTERN.matcher(parsed.coverUrl).matches()) {
                parsed.errors.add("封面链接必须以 http(s):// 开头");
            }
        }
        if (StringUtils.hasText(parsed.coverObjectKey)) {
            if (parsed.coverObjectKey.length() > COVER_KEY_MAX) {
                parsed.errors.add("封面对象键过长（上限" + COVER_KEY_MAX + "字符）");
            } else if (!parsed.coverObjectKey.startsWith(COVER_KEY_PREFIX)) {
                parsed.errors.add("封面对象键必须以 " + COVER_KEY_PREFIX + " 开头");
            } else if (coverKeyExists(parsed.coverObjectKey, coverKeyCache)) {
                parsed.coverKeyExists = true;
            } else {
                // 存储缺失仅行级警告不阻断（契约 §3.2），确认导入时该行封面不落库
                parsed.warnings.add("封面对象键「" + parsed.coverObjectKey + "」在存储中不存在，该行封面将不导入");
            }
        }
        validateNutrition(COL_CALORIES_PER_100G, parsed.caloriesPer100g, NUTRITION_MAX_PER_100G, parsed.errors);
        validateNutrition(COL_CALORIES_TOTAL, parsed.caloriesTotal, NUTRITION_MAX_TOTAL, parsed.errors);
        validateNutrition(COL_PROTEIN, parsed.proteinG, NUTRITION_MAX_NUTRIENT, parsed.errors);
        validateNutrition(COL_FAT, parsed.fatG, NUTRITION_MAX_NUTRIENT, parsed.errors);
        validateNutrition(COL_CARBS, parsed.carbsG, NUTRITION_MAX_NUTRIENT, parsed.errors);
        validateNutrition(COL_SODIUM, parsed.sodiumMg, NUTRITION_MAX_PER_100G, parsed.errors);
    }

    /** 营养数值合法性：负数 / 超 DB 列精度上限均记行级错误（防入库截断异常） */
    private void validateNutrition(String label, BigDecimal value, BigDecimal max, List<String> errors) {
        if (value == null) {
            return;
        }
        if (value.signum() < 0) {
            errors.add(label + "不能为负数");
        } else if (value.compareTo(max) > 0) {
            errors.add(label + "超出可录入范围（上限" + max.toPlainString() + "）");
        }
    }

    /**
     * 封面对象键存储存在性（MinIO statObject，参照 MinioStorageServiceImpl.initDefaultAvatar 用法）。
     * 对象不存在与存储暂不可达统一按「缺失」处理（契约 §3.2：缺失仅行级警告不阻断）；
     * 同批内按键缓存，重复对象键只查一次存储。
     */
    private boolean coverKeyExists(String objectKey, Map<String, Boolean> cache) {
        return cache.computeIfAbsent(objectKey, k -> {
            try {
                minioClient.statObject(StatObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(k)
                        .build());
                return true;
            } catch (Exception e) {
                log.warn("封面对象键存储校验未命中, objectKey: {}, 原因: {}", k, e.getMessage());
                return false;
            }
        });
    }

    /** 分类名 → ID 全量映射（一次 selectList，分类量级小；name 唯一索引裁决无重复名） */
    private Map<String, Long> loadCategoryIdByName() {
        Map<String, Long> map = new LinkedHashMap<>();
        for (DiningCategory category : diningCategoryMapper.selectList(null)) {
            map.put(category.getName(), category.getId());
        }
        return map;
    }

    private Long currentAdminId() {
        LoginUser currentUser = (LoginUser) securityUtil.getCurrentUser();
        if (currentUser == null) {
            throw new CustomExceptions("未获取到当前管理员身份");
        }
        return currentUser.getUserId();
    }

    // ==================== 上传解析（xlsx / CSV） ====================

    /** 上传文件 → 行表（首行为表头）。整体性问题（空文件/扩展名/格式损坏/超限）抛业务异常 */
    private List<List<String>> parseUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomExceptions("请选择要上传的导入文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new CustomExceptions("文件大小不能超过 5MB");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (filename.endsWith(".xlsx")) {
            return parseXlsx(file);
        }
        if (filename.endsWith(".csv")) {
            return parseCsv(file);
        }
        throw new CustomExceptions("仅支持 .xlsx 或 .csv 文件");
    }

    /** POI 解析 xlsx：按物理行号遍历（空行占位跳过），单元格统一转字符串 */
    private List<List<String>> parseXlsx(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.getBytes()))) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            List<List<String>> table = new ArrayList<>();
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    table.add(List.of());
                    continue;
                }
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    cells.add(formatter.formatCellValue(row.getCell(c)));
                }
                table.add(cells);
            }
            if (table.isEmpty()) {
                throw new CustomExceptions("文件不含任何数据行");
            }
            return table;
        } catch (CustomExceptions e) {
            throw e;
        } catch (Exception e) {
            log.warn("菜谱导入 xlsx 解析失败, filename: {}", file.getOriginalFilename(), e);
            throw new CustomExceptions("文件解析失败，请下载最新模板对照填写（仅支持 .xlsx / .csv）");
        }
    }

    /** Hutool 解析 CSV：编码自动探测（UTF-8 BOM / UTF-8 严格解码 / GBK 兜底） */
    private List<List<String>> parseCsv(MultipartFile file) {
        String text;
        try {
            byte[] bytes = file.getBytes();
            text = decodeCsvText(bytes);
        } catch (Exception e) {
            log.warn("菜谱导入 CSV 读取失败, filename: {}", file.getOriginalFilename(), e);
            throw new CustomExceptions("文件解析失败，请下载最新模板对照填写（仅支持 .xlsx / .csv）");
        }
        try {
            CsvReadConfig config = CsvReadConfig.defaultConfig();
            config.setContainsHeader(false); // 表头自己按列名匹配，第一行当普通行读出
            CsvData data = CsvUtil.getReader(config).read(new StringReader(text));
            List<CsvRow> csvRows = data.getRows();
            if (csvRows == null || csvRows.isEmpty()) {
                throw new CustomExceptions("文件不含任何数据行");
            }
            return csvRows.stream().map(CsvRow::getRawList).toList();
        } catch (CustomExceptions e) {
            throw e;
        } catch (Exception e) {
            log.warn("菜谱导入 CSV 解析失败, filename: {}", file.getOriginalFilename(), e);
            throw new CustomExceptions("文件解析失败，请下载最新模板对照填写（仅支持 .xlsx / .csv）");
        }
    }

    /** CSV 文本解码：UTF-8 BOM 直接按 UTF-8；无 BOM 先 UTF-8 严格解码，失败按 GBK（中文 Windows Excel 另存产物） */
    private String decodeCsvText(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("GBK"));
        }
    }


    /** 表头按列名匹配（顺序无关，重复列名取首个），缺任一必选列整体拒绝；v2 新列可选不校验 */
    private Map<String, Integer> resolveHeader(List<String> headerRow) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            String cellName = headerRow.get(i) == null ? "" : headerRow.get(i).trim();
            if (!cellName.isEmpty()) {
                map.putIfAbsent(cellName, i);
            }
        }
        List<String> required = List.of(COL_CATEGORY, COL_NAME, COL_INGREDIENTS, COL_STEPS, COL_COVER);
        List<String> missing = required.stream().filter(col -> !map.containsKey(col)).toList();
        if (!missing.isEmpty()) {
            throw new CustomExceptions("模板缺少列：" + String.join("、", missing)
                    + "（请下载最新模板对照填写）");
        }
        return map;
    }

    /** 取指定列文本（列下标越界 = 空串） */
    private String cell(List<String> row, Integer index) {
        if (index == null || index >= row.size()) {
            return "";
        }
        return row.get(index) == null ? "" : row.get(index);
    }

    /** 全空行判定（所有单元格空白） */
    private boolean isBlankRow(List<String> row) {
        return row.stream().allMatch(c -> !StringUtils.hasText(c));
    }

    /** 去两侧空白，空白串归一为 null（「未填写」语义） */
    private String trimToNull(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }
}
