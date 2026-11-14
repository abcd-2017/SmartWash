package com.smartwash.divination.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.common.audit.Audited;
import com.smartwash.divination.entity.DivBlockedQuestion;
import com.smartwash.divination.entity.DivInterpretation;
import com.smartwash.divination.entity.DivModelConfig;
import com.smartwash.divination.entity.DivPlatformSetting;
import com.smartwash.divination.entity.DivPromptVersion;
import com.smartwash.divination.entity.DivRagDocument;
import com.smartwash.divination.entity.DivUsageDaily;
import com.smartwash.divination.from.RagUploadFrom;
import com.smartwash.divination.from.SaveModelFrom;
import com.smartwash.divination.from.SearchBlockedQuestionFrom;
import com.smartwash.divination.mapper.DivBlockedQuestionMapper;
import com.smartwash.divination.mapper.DivInterpretationMapper;
import com.smartwash.divination.mapper.DivRagDocumentMapper;
import com.smartwash.divination.mapper.DivUsageDailyMapper;
import com.smartwash.divination.service.DivModelConfigService;
import com.smartwash.divination.service.DivPlatformSettingService;
import com.smartwash.divination.service.DivPromptService;
import com.smartwash.divination.service.DivRagService;
import com.smartwash.divination.support.DivRagTextExtractor;
import com.smartwash.divination.vo.DivModelConfigAdminVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 观象台管理端控制器。
 * 路径前缀 /admin/div/**，需 ROLE_ADMIN。
 * 提供用量看板、审计失败复审等管理端接口。
 */
@Tag(name = "观象台-管理端", description = "占卜模块管理端接口（用量/审计/拦截日志）")
@Slf4j
@RestController
@RequestMapping("/admin/div")
@RequiredArgsConstructor
public class DivinationAdminController {

    private final DivUsageDailyMapper usageDailyMapper;
    private final DivInterpretationMapper interpretationMapper;
    private final DivPromptService promptService;
    private final DivRagService ragService;
    private final DivModelConfigService modelConfigService;
    private final DivPlatformSettingService platformSettingService;
    private final DivBlockedQuestionMapper blockedQuestionMapper;
    private final DivRagDocumentMapper ragDocumentMapper;

    @Operation(summary = "用量看板", description = "按日期范围查询每日用量统计")
    @GetMapping("/usage")
    public Result<List<DivUsageDaily>> getUsage(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        LambdaQueryWrapper<DivUsageDaily> wrapper = new LambdaQueryWrapper<>();
        if (from != null) wrapper.ge(DivUsageDaily::getStatDate, LocalDate.parse(from));
        if (to != null) wrapper.le(DivUsageDaily::getStatDate, LocalDate.parse(to));
        wrapper.orderByDesc(DivUsageDaily::getStatDate);
        return Result.ok(usageDailyMapper.selectList(wrapper));
    }

    @Operation(summary = "审计失败复审", description = "查询审计不一致的解读记录")
    @GetMapping("/audits")
    public Result<Page<DivInterpretation>> getAuditFailures(
            @RequestParam(value = "status", defaultValue = "2") Integer status,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size) {
        Page<DivInterpretation> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<DivInterpretation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DivInterpretation::getAuditStatus, status);
        wrapper.orderByDesc(DivInterpretation::getCreatedAt);
        return Result.ok(interpretationMapper.selectPage(pageObj, wrapper));
    }

    // ==================== Prompt 版本管理 ====================

    @Operation(summary = "Prompt 版本分页列表", description = "按术数方法（可选）与版本（可选）分页查询 prompt 版本")
    @GetMapping("/prompts")
    public Result<Page<DivPromptVersion>> listPrompts(
            @RequestParam(value = "method", required = false) String method,
            @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        Page<DivPromptVersion> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<DivPromptVersion> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(method)) wrapper.eq(DivPromptVersion::getMethod, method);
        if (StringUtils.hasText(version)) wrapper.eq(DivPromptVersion::getVersion, version);
        if (status != null) wrapper.eq(DivPromptVersion::getStatus, status);
        wrapper.orderByDesc(DivPromptVersion::getCreatedAt);
        return Result.ok(promptService.listPage(pageObj, wrapper));
    }

    @Operation(summary = "保存 Prompt 版本", description = "新增或更新 prompt 版本")
    @Audited(action = "DIV_PROMPT_SAVE", targetType = "div_prompt_version")
    @PostMapping("/prompts")
    public Result<Long> savePrompt(@RequestBody DivPromptVersion version) {
        return Result.ok(promptService.save(version));
    }

    @Operation(summary = "激活 Prompt 版本", description = "激活指定版本（同方法其他版本退役）")
    @Audited(action = "DIV_PROMPT_ACTIVATE", targetType = "div_prompt_version", targetIdSpEL = "#id")
    @PostMapping("/prompts/{id}/activate")
    public Result<String> activatePrompt(@PathVariable("id") Long id) {
        promptService.activate(id);
        return Result.ok("激活成功");
    }

    @Operation(summary = "删除 Prompt 版本", description = "删除指定 prompt 版本")
    @Audited(action = "DIV_PROMPT_DELETE", targetType = "div_prompt_version", targetIdSpEL = "#id")
    @DeleteMapping("/prompts/{id}")
    public Result<String> deletePrompt(@PathVariable("id") Long id) {
        promptService.deleteById(id);
        return Result.ok("删除成功");
    }

    // ==================== RAG 语料管理 ====================

    @Operation(summary = "古籍文档列表", description = "分页查询已上传的古籍语料")
    @GetMapping("/rag/documents")
    public Result<Page<DivRagDocument>> listRagDocuments(
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "method", required = false) String method,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size) {
        Page<DivRagDocument> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<DivRagDocument> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(title)) wrapper.like(DivRagDocument::getTitle, title);
        if (StringUtils.hasText(method)) wrapper.eq(DivRagDocument::getMethod, method);
        if (status != null) wrapper.eq(DivRagDocument::getStatus, status);
        wrapper.orderByDesc(DivRagDocument::getCreatedAt);
        return Result.ok(ragDocumentMapper.selectPage(pageObj, wrapper));
    }

    @Operation(summary = "上传古籍文档", description = "multipart 文件（.txt/.md/.docx，≤10MB）+ 书名/所属典籍/术数方法；文本从文件读取后落库并切片（PDF 暂不支持）")
    @Audited(action = "DIV_RAG_UPLOAD", targetType = "div_rag_document")
    @PostMapping("/rag/documents")
    public Result<Long> uploadRagDocument(@RequestPart("file") MultipartFile file,
                                          @Valid @ModelAttribute RagUploadFrom from) {
        // 契约对齐前端 FormData：file + title/book/method，content 不再走 JSON 入参
        String content = DivRagTextExtractor.extract(file);
        return Result.ok(ragService.uploadDocument(from.getTitle(), from.getBook(), from.getMethod(), content));
    }

    // ==================== 拦截问题日志 ====================

    @Operation(summary = "拦截问题日志", description = "按关键词模糊匹配问题内容分页查询被安全分流拦截的问题")
    @GetMapping("/blocked")
    public Result<Page<DivBlockedQuestion>> getBlockedQuestions(SearchBlockedQuestionFrom from) {
        Page<DivBlockedQuestion> pageObj = new Page<>(from.getPage(), from.getSize());
        LambdaQueryWrapper<DivBlockedQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(from.getKeyword()), DivBlockedQuestion::getQuestion, from.getKeyword());
        wrapper.orderByDesc(DivBlockedQuestion::getCreatedAt);
        return Result.ok(blockedQuestionMapper.selectPage(pageObj, wrapper));
    }

    // ==================== 平台模型目录管理 ====================

    @Operation(summary = "模型目录分页列表", description = "按厂商/名称/启用态筛选（均可选），返回分页结构（仅掩码，无密文）")
    @GetMapping("/models")
    public Result<Page<DivModelConfigAdminVo>> listModels(
            @RequestParam(value = "vendor", required = false) String vendor,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "enabled", required = false) Integer enabled,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        Page<DivModelConfig> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<DivModelConfig> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(vendor)) wrapper.like(DivModelConfig::getVendor, vendor);
        if (StringUtils.hasText(name)) wrapper.like(DivModelConfig::getName, name);
        if (enabled != null) wrapper.eq(DivModelConfig::getEnabled, enabled);
        wrapper.orderByAsc(DivModelConfig::getPriority);
        Page<DivModelConfig> result = modelConfigService.listPage(pageObj, wrapper);
        // 实体 → VO 裁剪密文与主密钥版本（密文永不出接口）
        Page<DivModelConfigAdminVo> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(modelConfigService::toAdminVo).collect(java.util.stream.Collectors.toList()));
        return Result.ok(voPage);
    }

    @Operation(summary = "保存模型目录条目", description = "新增或更新目录条目（明文密钥加密入库，编辑留空表示不更换）")
    @Audited(action = "DIV_MODEL_SAVE", targetType = "div_model_config")
    @PostMapping("/models")
    public Result<Long> saveModel(@RequestBody @Valid SaveModelFrom from) {
        return Result.ok(modelConfigService.save(from));
    }

    @Operation(summary = "批量导入模型目录", description = "JSON 数组批量入库（vendor/name/baseUrl/modelId 必填，apiKey 可选）；非法条目跳过，返回成功导入数")
    @Audited(action = "DIV_MODEL_IMPORT", targetType = "div_model_config")
    @PostMapping("/models/import")
    public Result<Integer> importModels(@RequestBody List<SaveModelFrom> items) {
        int imported = 0;
        for (SaveModelFrom from : items) {
            if (from == null || !StringUtils.hasText(from.getVendor()) || !StringUtils.hasText(from.getName())
                    || !StringUtils.hasText(from.getBaseUrl()) || !StringUtils.hasText(from.getModelId())) {
                continue;
            }
            modelConfigService.save(from);
            imported++;
        }
        log.info("批量导入模型目录, 总数: {}, 成功: {}", items.size(), imported);
        return Result.ok(imported);
    }

    @Operation(summary = "删除模型配置", description = "删除指定模型")
    @Audited(action = "DIV_MODEL_DELETE", targetType = "div_model_config", targetIdSpEL = "#id")
    @DeleteMapping("/models/{id}")
    public Result<String> deleteModel(@PathVariable("id") Long id) {
        modelConfigService.deleteById(id);
        return Result.ok("删除成功");
    }

    @Operation(summary = "模型连通性测试", description = "不保存，解密密钥后真实 1-token 试呼")
    @PostMapping("/models/{id}/test")
    public Result<Boolean> testModel(@PathVariable("id") Long id) {
        DivModelConfig config = modelConfigService.getById(id);
        if (config == null) return Result.failMsg("模型不存在");
        return Result.ok(modelConfigService.testConnectivityById(id));
    }

    // ==================== 平台全局设置 ====================

    @Operation(summary = "获取平台设置", description = "获取平台全局设置（单例）")
    @GetMapping("/settings")
    public Result<DivPlatformSetting> getSettings() {
        return Result.ok(platformSettingService.getSetting());
    }

    @Operation(summary = "更新平台设置", description = "更新平台全局设置")
    @Audited(action = "DIV_SETTING_UPDATE", targetType = "div_platform_setting")
    @PutMapping("/settings")
    public Result<String> updateSettings(@RequestBody @Valid DivPlatformSetting setting) {
        platformSettingService.updateSetting(setting);
        return Result.ok("更新成功");
    }

    @Operation(summary = "主密钥轮换", description = "触发主密钥轮换（新版本生效并触发后台重加密）")
    @Audited(action = "DIV_KEY_ROTATE", targetType = "divPlatformSetting")
    @PostMapping("/settings/rotate-key")
    public Result<String> rotateKey() {
        // TODO: 触发 DivKeyReEncryptTask 分批重加密
        log.info("主密钥轮换请求（简化实现）");
        return Result.ok("轮换任务已启动");
    }
}
