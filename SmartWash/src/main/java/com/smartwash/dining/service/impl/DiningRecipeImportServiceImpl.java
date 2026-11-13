package com.smartwash.dining.service.impl;

import cn.hutool.core.text.csv.CsvData;
import cn.hutool.core.text.csv.CsvReadConfig;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.text.csv.CsvUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwash.dining.entity.DiningCategory;
import com.smartwash.dining.entity.DiningRecipe;
import com.smartwash.dining.from.DiningRecipeImportConfirmFrom;
import com.smartwash.dining.mapper.DiningCategoryMapper;
import com.smartwash.dining.mapper.DiningRecipeMapper;
import com.smartwash.dining.service.DiningRecipeImportService;
import com.smartwash.dining.vo.DiningRecipeImportPreviewVo;
import com.smartwash.dining.vo.DiningRecipeImportResultVo;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.utils.JsonUtils;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
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
import java.util.regex.Pattern;

/**
 * 菜谱批量导入服务实现（03 号票）。
 *
 * <p>两阶段导入：预览（解析 + 逐行校验，不落库）→ 确认（整批事务写入 + 逐行报告）。
 * 事务语义对齐游戏模块关卡批量导入先例：确认阶段再次逐行校验，任一行失败全批不落库，
 * 杜绝半批状态；全部通过才逐行插入。</p>
 *
 * <p>格式支持：.xlsx（POI）与 .csv（Hutool，编码自动探测 UTF-8 BOM / UTF-8 / GBK——
 * Excel 另存 CSV 在中文 Windows 上常为 GBK）。表头按列名匹配（顺序无关），列名：
 * 分类名 / 菜名 / 食材名列表 / 步骤 / 封面链接。</p>
 *
 * <p>取舍说明：封面链接列仅做格式校验，导入不落库（dining_recipe.cover_object_key 存
 * MinIO 对象键，外部 URL 无法当对象键直链拼装，导入时批量下载外部图片又引入 SSRF 风险）——
 * 封面走 Web 端单独上传链路，模板与预览报告中均明示。模板为接口动态生成而非静态文件：
 * 列约束与校验规则同源演进，不依赖 jar 内资源路径维护。</p>
 */
@Slf4j
@Service
public class DiningRecipeImportServiceImpl implements DiningRecipeImportService {

    // ==================== 契约常量 ====================

    /** 单次导入数据行上限（对齐关卡批量导入 500 上限惯例，防超大请求） */
    public static final int MAX_IMPORT_ROWS = 500;

    /** 上传文件大小上限（5MB，足够承载 500 行文本） */
    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    /** 模板列名（表头按名匹配、顺序无关） */
    private static final String COL_CATEGORY = "分类名";
    private static final String COL_NAME = "菜名";
    private static final String COL_INGREDIENTS = "食材名列表";
    private static final String COL_STEPS = "步骤";
    private static final String COL_COVER = "封面链接";

    /** 食材名分隔符：顿号 / 中文逗号 / 英文逗号 / 中英文分号均兼容（模板填写说明同步声明） */
    private static final Pattern INGREDIENT_DELIMITER = Pattern.compile("[、，,;；]");

    /** 封面链接格式：仅接受 http(s) 外链 */
    private static final Pattern COVER_URL_PATTERN = Pattern.compile("^https?://\\S+$");

    /** 字段长度上限（与 AddDiningRecipeFrom 约束对齐） */
    private static final int NAME_MAX = 64;
    private static final int CATEGORY_MAX = 32;
    private static final int INGREDIENT_MAX = 32;
    private static final int INGREDIENT_COUNT_MAX = 100;
    private static final int STEPS_MAX = 20000;
    private static final int COVER_MAX = 500;

    private static final String SOURCE_ADMIN = "ADMIN";

    /** 菜谱 Mapper：确认阶段逐行写入（insert 后 MyBatis 回填自增 ID 进逐行报告） */
    @Autowired
    private DiningRecipeMapper diningRecipeMapper;

    /** 分类 Mapper：分类名 → ID 映射（一次全量拉取，分类量级小） */
    @Autowired
    private DiningCategoryMapper diningCategoryMapper;

    /** 当前登录上下文（确认导入记录创建管理员） */
    @Autowired
    private SecurityUtil securityUtil;

    // ==================== 模板生成 ====================

    @Override
    public byte[] buildTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("菜谱导入");
            // 表头
            Row header = sheet.createRow(0);
            for (int i = 0; i < 5; i++) {
                header.createCell(i).setCellValue(switch (i) {
                    case 0 -> COL_CATEGORY;
                    case 1 -> COL_NAME;
                    case 2 -> COL_INGREDIENTS;
                    case 3 -> COL_STEPS;
                    default -> COL_COVER;
                });
            }
            // 示例行（模板自身可被 preview 正确解析，方便运营对照）
            sheet.createRow(1).createCell(1).setCellValue("番茄炒蛋");
            sheet.getRow(1).createCell(2).setCellValue("西红柿、鸡蛋、葱");
            sheet.getRow(1).createCell(3).setCellValue("1. 打蛋调匀\n2. 热锅下油炒蛋\n3. 下西红柿翻匀出锅");
            sheet.createRow(2).createCell(1).setCellValue("凉拌黄瓜");
            sheet.getRow(2).createCell(2).setCellValue("黄瓜；蒜");
            sheet.getRow(2).createCell(3).setCellValue("黄瓜拍碎，加蒜拌匀");
            sheet.setColumnWidth(2, 16 * 256);
            sheet.setColumnWidth(3, 40 * 256);

            // 填写说明 sheet（首行总述，后续逐列说明）
            Sheet explain = workbook.createSheet("填写说明");
            explain.createRow(0).createCell(0).setCellValue(
                    "填写说明：每行一道菜；食材之间用顿号、逗号或分号分隔；"
                            + "分类名必须已存在（可先在「分类管理」页创建，留空为未分类）；"
                            + "封面链接当前版本仅校验格式不导入，封面请在导入后于本页单独上传；"
                            + "单次最多导入 500 行。");
            String[] columnTips = {
                    "分类名（可空=未分类）：必须与「分类管理」页已有分类完全一致",
                    "菜名（必填）：最长 64 字，允许重名",
                    "食材名列表：食材名之间用顿号、逗号或分号分隔；单个食材名最长 32 字、最多 100 项；只填名称，无需数量",
                    "步骤（可空）：最长 20000 字，换行分段",
                    "封面链接（可空）：仅校验 http(s):// 开头，当前版本不导入，封面请在导入后单独上传",
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

        Map<String, Long> categoryIdByName = loadCategoryIdByName();
        long dataRowCount = dataRows.stream().filter(r -> !isBlankRow(r)).count();
        if (dataRowCount > MAX_IMPORT_ROWS) {
            throw new CustomExceptions("单次最多导入 " + MAX_IMPORT_ROWS + " 行，当前 " + dataRowCount + " 行");
        }
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
            DiningRecipeImportPreviewVo.PreviewRow previewRow = buildPreviewRow(rowIndex, row, columnMap, categoryIdByName);
            vo.getRows().add(previewRow);
            if (previewRow.getErrors().isEmpty()) {
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

    /** 组装单行预览条目：解析归一 + 逐行校验（校验规则与确认阶段共用） */
    private DiningRecipeImportPreviewVo.PreviewRow buildPreviewRow(int rowNo, List<String> row,
                                                                   Map<String, Integer> columnMap,
                                                                   Map<String, Long> categoryIdByName) {
        String categoryName = cell(row, columnMap.get(COL_CATEGORY));
        String name = cell(row, columnMap.get(COL_NAME));
        String ingredientsRaw = cell(row, columnMap.get(COL_INGREDIENTS));
        String steps = cell(row, columnMap.get(COL_STEPS));
        String coverUrl = cell(row, columnMap.get(COL_COVER));

        List<String> ingredients = StringUtils.hasText(ingredientsRaw)
                ? Arrays.stream(INGREDIENT_DELIMITER.split(ingredientsRaw))
                .map(String::trim).filter(s -> !s.isEmpty()).toList()
                : List.of();

        DiningRecipeImportPreviewVo.PreviewRow previewRow = new DiningRecipeImportPreviewVo.PreviewRow()
                .setRowNo(rowNo)
                .setCategoryName(StringUtils.hasText(categoryName) ? categoryName.trim() : null)
                .setCategoryId(StringUtils.hasText(categoryName)
                        ? categoryIdByName.get(categoryName.trim()) : null)
                .setName(StringUtils.hasText(name) ? name.trim() : null)
                .setIngredients(ingredients)
                .setSteps(StringUtils.hasText(steps) ? steps.trim() : null)
                .setCoverUrl(StringUtils.hasText(coverUrl) ? coverUrl.trim() : null)
                .setErrors(new ArrayList<>());

        validateRow(previewRow.getCategoryName(), previewRow.getName(), ingredients,
                previewRow.getSteps(), previewRow.getCoverUrl(), categoryIdByName, previewRow.getErrors());
        return previewRow;
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

        // 确认阶段重新逐行校验（防绕过预览直接提交；预览与确认之间分类可能被删除）
        Map<String, Long> categoryIdByName = loadCategoryIdByName();
        List<DiningRecipeImportResultVo.ResultRow> reportRows = new ArrayList<>();
        // 校验通过行的 (报告行, 待插入实体) 有序列表：报告行与实体一一对应，插入后回填 recipeId
        List<Map.Entry<DiningRecipeImportResultVo.ResultRow, DiningRecipe>> pending = new ArrayList<>();
        boolean anyFailed = false;
        for (DiningRecipeImportConfirmFrom.Row row : rows) {
            DiningRecipeImportResultVo.ResultRow reportRow = new DiningRecipeImportResultVo.ResultRow()
                    .setRowNo(row.getRowNo()).setName(row.getName());
            List<String> errors = new ArrayList<>();
            String categoryName = StringUtils.hasText(row.getCategoryName()) ? row.getCategoryName().trim() : null;
            List<String> ingredients = row.getIngredients() == null ? List.of() : row.getIngredients();
            validateRow(categoryName,
                    StringUtils.hasText(row.getName()) ? row.getName().trim() : null,
                    ingredients,
                    StringUtils.hasText(row.getSteps()) ? row.getSteps().trim() : null,
                    null, categoryIdByName, errors);

            if (!errors.isEmpty()) {
                anyFailed = true;
                reportRow.setSuccess(false).setError(String.join("；", errors));
            } else {
                pending.add(Map.entry(reportRow, buildRecipe(categoryName, row.getName().trim(), ingredients,
                        StringUtils.hasText(row.getSteps()) ? row.getSteps().trim() : null, categoryIdByName)));
                reportRow.setSuccess(true);
            }
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

        int successCount = 0;
        for (Map.Entry<DiningRecipeImportResultVo.ResultRow, DiningRecipe> entry : pending) {
            DiningRecipe recipe = entry.getValue();
            diningRecipeMapper.insert(recipe);
            entry.getKey().setRecipeId(recipe.getId());
            successCount++;
        }
        vo.setRolledBack(false).setSuccessCount(successCount).setFailedCount(0);
        log.info("菜谱批量导入完成, 总数: {}, 成功: {}", rows.size(), successCount);
        return vo;
    }

    /** 校验通过 → 构造落库实体（source=ADMIN、默认上架、创建管理员、食材 JSON 序列化） */
    private DiningRecipe buildRecipe(String categoryName, String name, List<String> ingredients,
                                     String steps, Map<String, Long> categoryIdByName) {
        DiningRecipe recipe = new DiningRecipe();
        recipe.setName(name);
        recipe.setCategoryId(categoryName == null ? null : categoryIdByName.get(categoryName));
        recipe.setIngredients(ingredients.isEmpty() ? null : JsonUtils.toJson(ingredients));
        recipe.setSteps(steps);
        recipe.setSource(SOURCE_ADMIN);
        recipe.setStatus(DiningRecipe.STATUS_ON_SHELF);
        recipe.setCreatedByAdminId(currentAdminId());
        return recipe;
    }

    // ==================== 共用校验（预览与确认同一套规则） ====================

    /**
     * 行级校验：错误追加进 errors（空列表 = 通过）。分类按名精确匹配，不存在报错（不自动建，
     * 对齐任务票「非法分类给行号级错误」要求）；封面链接仅校验格式（不落库的取舍见类注释）。
     */
    private void validateRow(String categoryName, String name, List<String> ingredients,
                             String steps, String coverUrl, Map<String, Long> categoryIdByName,
                             List<String> errors) {
        if (!StringUtils.hasText(name)) {
            errors.add("菜名不能为空");
        } else if (name.length() > NAME_MAX) {
            errors.add("菜名过长（上限" + NAME_MAX + "字符）");
        }
        if (StringUtils.hasText(categoryName)) {
            if (categoryName.length() > CATEGORY_MAX) {
                errors.add("分类名过长（上限" + CATEGORY_MAX + "字符）");
            } else if (!categoryIdByName.containsKey(categoryName)) {
                errors.add("分类「" + categoryName + "」不存在（请先在分类管理页创建，或留空为未分类）");
            }
        }
        if (ingredients.size() > INGREDIENT_COUNT_MAX) {
            errors.add("食材数量过多（上限" + INGREDIENT_COUNT_MAX + "项）");
        }
        for (String ingredient : ingredients) {
            if (ingredient.length() > INGREDIENT_MAX) {
                errors.add("食材名「" + ingredient + "」过长（上限" + INGREDIENT_MAX + "字符）");
                break; // 同类错误报一次即可，避免报告刷屏
            }
        }
        if (StringUtils.hasText(steps) && steps.length() > STEPS_MAX) {
            errors.add("步骤内容过长（上限" + STEPS_MAX + "字符）");
        }
        if (StringUtils.hasText(coverUrl)) {
            if (coverUrl.length() > COVER_MAX) {
                errors.add("封面链接过长（上限" + COVER_MAX + "字符）");
            } else if (!COVER_URL_PATTERN.matcher(coverUrl).matches()) {
                errors.add("封面链接必须以 http(s):// 开头");
            }
        }
    }

    /** 分类名 → ID 全量映射（一次 selectList，分类量级小；name 唯一索引裁决无重复名） */
    private Map<String, Long> loadCategoryIdByName() {
        Map<String, Long> map = new LinkedHashMap<>();
        for (DiningCategory category : diningCategoryMapper.selectList(null)) {
            map.put(category.getName(), category.getId());
        }
        return map;
    }

    /** 当前登录管理员 ID */
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

    // ==================== 表头与单元格工具 ====================

    /** 表头按列名匹配（顺序无关，重复列名取首个），缺任一必需列整体拒绝；返回 列名 → 列下标 */
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
}
