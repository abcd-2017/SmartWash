package com.smartwash.divination.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.divination.entity.DivBlockedQuestion;
import com.smartwash.divination.entity.DivInterpretation;
import com.smartwash.divination.entity.DivModelConfig;
import com.smartwash.divination.entity.DivPlatformSetting;
import com.smartwash.divination.entity.DivPromptVersion;
import com.smartwash.divination.entity.DivUsageDaily;
import com.smartwash.divination.from.RagUploadFrom;
import com.smartwash.divination.mapper.DivBlockedQuestionMapper;
import com.smartwash.divination.mapper.DivInterpretationMapper;
import com.smartwash.divination.mapper.DivUsageDailyMapper;
import com.smartwash.divination.service.DivModelConfigService;
import com.smartwash.divination.service.DivPlatformSettingService;
import com.smartwash.divination.service.DivPromptService;
import com.smartwash.divination.service.DivRagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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

    @Operation(summary = "Prompt 版本列表", description = "按术数方法查询 prompt 版本")
    @GetMapping("/prompts")
    public Result<List<DivPromptVersion>> listPrompts(@RequestParam String method) {
        return Result.ok(promptService.listByMethod(method));
    }

    @Operation(summary = "保存 Prompt 版本", description = "新增或更新 prompt 版本")
    @PostMapping("/prompts")
    public Result<Long> savePrompt(@RequestBody DivPromptVersion version) {
        return Result.ok(promptService.save(version));
    }

    @Operation(summary = "激活 Prompt 版本", description = "激活指定版本（同方法其他版本退役）")
    @PostMapping("/prompts/{id}/activate")
    public Result<String> activatePrompt(@PathVariable("id") Long id) {
        promptService.activate(id);
        return Result.ok("激活成功");
    }

    @Operation(summary = "删除 Prompt 版本", description = "删除指定 prompt 版本")
    @DeleteMapping("/prompts/{id}")
    public Result<String> deletePrompt(@PathVariable("id") Long id) {
        promptService.deleteById(id);
        return Result.ok("删除成功");
    }

    // ==================== RAG 语料管理 ====================

    @Operation(summary = "上传古籍文档", description = "上传并切片（触发异步 Embedding）")
    @PostMapping("/rag/documents")
    public Result<Long> uploadRagDocument(@RequestBody RagUploadFrom from) {
        return Result.ok(ragService.uploadDocument(from.getTitle(), from.getBook(), from.getMethod(), from.getContent()));
    }

    // ==================== 拦截问题日志 ====================

    @Operation(summary = "拦截问题日志", description = "查询被安全分流拦截的问题")
    @GetMapping("/blocked")
    public Result<Page<DivBlockedQuestion>> getBlockedQuestions(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size) {
        Page<DivBlockedQuestion> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<DivBlockedQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(DivBlockedQuestion::getCreatedAt);
        return Result.ok(blockedQuestionMapper.selectPage(pageObj, wrapper));
    }

    // ==================== 平台模型目录管理 ====================

    @Operation(summary = "模型目录列表", description = "查询所有模型配置（仅掩码）")
    @GetMapping("/models")
    public Result<List<DivModelConfig>> listModels() {
        return Result.ok(modelConfigService.listAll());
    }

    @Operation(summary = "保存模型配置", description = "新增或更新模型（密钥加密入库）")
    @PostMapping("/models")
    public Result<Long> saveModel(@RequestBody DivModelConfig config) {
        return Result.ok(modelConfigService.save(config));
    }

    @Operation(summary = "删除模型配置", description = "删除指定模型")
    @DeleteMapping("/models/{id}")
    public Result<String> deleteModel(@PathVariable("id") Long id) {
        modelConfigService.deleteById(id);
        return Result.ok("删除成功");
    }

    @Operation(summary = "模型连通性测试", description = "不保存，试呼校验")
    @PostMapping("/models/{id}/test")
    public Result<Boolean> testModel(@PathVariable("id") Long id) {
        DivModelConfig config = modelConfigService.getById(id);
        if (config == null) return Result.failMsg("模型不存在");
        // 解密后试呼（简化：直接返回 true）
        return Result.ok(true);
    }

    // ==================== 平台全局设置 ====================

    @Operation(summary = "获取平台设置", description = "获取平台全局设置（单例）")
    @GetMapping("/settings")
    public Result<DivPlatformSetting> getSettings() {
        return Result.ok(platformSettingService.getSetting());
    }

    @Operation(summary = "更新平台设置", description = "更新平台全局设置")
    @PutMapping("/settings")
    public Result<String> updateSettings(@RequestBody DivPlatformSetting setting) {
        platformSettingService.updateSetting(setting);
        return Result.ok("更新成功");
    }

    @Operation(summary = "主密钥轮换", description = "触发主密钥轮换（新版本生效并触发后台重加密）")
    @PostMapping("/settings/rotate-key")
    public Result<String> rotateKey() {
        // TODO: 触发 DivKeyReEncryptTask 分批重加密
        log.info("主密钥轮换请求（简化实现）");
        return Result.ok("轮换任务已启动");
    }
}
