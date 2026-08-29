package com.smartwash.divination.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.divination.from.CreateRecordFrom;
import com.smartwash.divination.from.FeedbackFrom;
import com.smartwash.divination.from.SaveUserApiFrom;
import com.smartwash.divination.from.SearchRecordFrom;
import com.smartwash.divination.service.DivModelConfigService;
import com.smartwash.divination.service.DivUserApiConfigService;
import com.smartwash.divination.service.IDivInterpretationService;
import com.smartwash.divination.service.IDivRecordService;
import com.smartwash.divination.vo.MethodVo;
import com.smartwash.divination.vo.ModelVo;
import com.smartwash.divination.vo.RecordDetailVo;
import com.smartwash.divination.vo.RecordVo;
import com.smartwash.divination.vo.UserApiConfigVo;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 观象台用户端控制器。
 * 路径前缀 /web/auth/div/**，需 ROLE_USER。
 * 接口契约必须与 Android 端 DivinationApi.kt 匹配。
 */
@Tag(name = "观象台-用户端", description = "占卜卦例管理与解读接口")
@Slf4j
@RestController
@RequestMapping("/web/auth/div")
@RequiredArgsConstructor
public class DivinationWebController {

    private final IDivRecordService recordService;
    private final IDivInterpretationService interpretationService;
    private final DivModelConfigService modelConfigService;
    private final DivUserApiConfigService userApiConfigService;

    @Operation(summary = "创建卦例", description = "服务端 core 重算权威盘面并与客户端盘面比对校验")
    @PostMapping("/records")
    public Result<Long> createRecord(@RequestBody @Valid CreateRecordFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(recordService.createRecord(from, user.getUserId()));
    }

    @Operation(summary = "卦历分页", description = "分页查询当前用户的卦例历史")
    @GetMapping("/records")
    public Result<Page<RecordVo>> searchRecords(SearchRecordFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(recordService.searchRecords(from, user.getUserId()));
    }

    @Operation(summary = "卦例详情", description = "查询单个卦例详情（含最近一次解读）")
    @GetMapping("/records/{id}")
    public Result<RecordDetailVo> getRecordDetail(@PathVariable("id") Long id) {
        LoginUser user = UserContextHolder.getUser();
        RecordDetailVo detail = recordService.getRecordDetail(id, user.getUserId());
        if (detail == null) {
            return Result.failMsg("卦例不存在");
        }
        return Result.ok(detail);
    }

    @Operation(summary = "今日一签", description = "服务端按当日时间卦生成/复用卦例")
    @GetMapping("/today")
    public Result<RecordDetailVo> getTodayRecord() {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(recordService.getTodayRecord(user.getUserId()));
    }

    @Operation(summary = "SSE 流式解读", description = "对卦例进行 LLM 流式解读（event: delta/done/error）")
    @PostMapping("/records/{id}/interpret")
    public SseEmitter interpret(@PathVariable("id") Long id, @RequestBody(required = false) String question) {
        LoginUser user = UserContextHolder.getUser();
        return interpretationService.interpret(id, question, user.getUserId(), false);
    }

    @Operation(summary = "同卦追问", description = "对同一卦例进行追问（SSE 流式）")
    @PostMapping("/records/{id}/followup")
    public SseEmitter followup(@PathVariable("id") Long id, @RequestBody String question) {
        LoginUser user = UserContextHolder.getUser();
        return interpretationService.interpret(id, question, user.getUserId(), true);
    }

    @Operation(summary = "反馈与应验回填", description = "对解读进行评分（1-5）和应验回填")
    @PostMapping("/records/{id}/feedback")
    public Result<String> feedback(@PathVariable("id") Long id, @RequestBody @Valid FeedbackFrom from) {
        LoginUser user = UserContextHolder.getUser();
        recordService.addFeedback(id, from, user.getUserId());
        return Result.ok("反馈已提交");
    }

    @Operation(summary = "四术元信息", description = "获取四术元信息与免责声明文案")
    @GetMapping("/methods")
    public Result<java.util.List<MethodVo>> getMethods() {
        java.util.List<MethodVo> methods = new java.util.ArrayList<>();
        methods.add(createMethod("liuyao", "六爻纳甲", "具体一事的成败/吉凶/应期，摇卦起卦"));
        methods.add(createMethod("meihua", "梅花易数", "快占一事、万物类象，时间/数字起卦"));
        methods.add(createMethod("qimen", "奇门遁甲", "占事+择时+择方位，时间起局"));
        methods.add(createMethod("liuren", "大六壬", "占事（人事最精），时间起课"));
        return Result.ok(methods);
    }

    @Operation(summary = "可选模型目录", description = "获取平台启用的模型目录")
    @GetMapping("/models")
    public Result<java.util.List<ModelVo>> getModels() {
        java.util.List<ModelVo> models = new java.util.ArrayList<>();
        modelConfigService.listEnabled().forEach(config -> {
            ModelVo vo = new ModelVo();
            vo.setId(config.getId());
            vo.setName(config.getName());
            vo.setProvider(config.getProvider());
            vo.setModelId(config.getModelId());
            vo.setPriority(config.getPriority());
            vo.setActive(Boolean.FALSE);
            models.add(vo);
        });
        return Result.ok(models);
    }

    @Operation(summary = "获取用户 BYOK 配置", description = "仅返回掩码，无明文/密文")
    @GetMapping("/settings/api")
    public Result<UserApiConfigVo> getUserApiConfig() {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(userApiConfigService.getUserConfig(user.getUserId()));
    }

    @Operation(summary = "保存/更换 BYOK", description = "连通性校验 → AES-GCM 加密入库")
    @PutMapping("/settings/api")
    public Result<String> saveUserApiConfig(@RequestBody SaveUserApiFrom from) {
        LoginUser user = UserContextHolder.getUser();
        userApiConfigService.saveUserConfig(user.getUserId(), from.getBaseUrl(), from.getModel(), from.getApiKey());
        return Result.ok("保存成功");
    }

    @Operation(summary = "删除 BYOK", description = "删除用户自带 API 配置")
    @DeleteMapping("/settings/api")
    public Result<String> deleteUserApiConfig() {
        LoginUser user = UserContextHolder.getUser();
        userApiConfigService.deleteUserConfig(user.getUserId());
        return Result.ok("删除成功");
    }

    @Operation(summary = "连通性测试", description = "不保存，独立限流")
    @PostMapping("/settings/api/test")
    public Result<Boolean> testUserApi(@RequestBody SaveUserApiFrom from) {
        boolean ok = modelConfigService.testConnectivity(from.getBaseUrl(), from.getApiKey(), from.getModel());
        return Result.ok(ok);
    }

    private MethodVo createMethod(String code, String name, String desc) {
        MethodVo vo = new MethodVo();
        vo.setCode(code);
        vo.setName(name);
        vo.setDescription(desc);
        vo.setDisclaimer("本解读仅供娱乐参考，不构成任何建议");
        return vo;
    }
}
