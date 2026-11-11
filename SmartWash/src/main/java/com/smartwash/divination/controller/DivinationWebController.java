package com.smartwash.divination.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.common.ratelimit.RateLimit;
import com.smartwash.divination.from.CreateRecordFrom;
import com.smartwash.divination.from.FeedbackFrom;
import com.smartwash.divination.from.SaveUserApiFrom;
import com.smartwash.divination.from.SearchRecordFrom;
import com.smartwash.divination.service.DivModelConfigService;
import com.smartwash.divination.service.DivUserApiConfigService;
import com.smartwash.divination.service.IDivInterpretationService;
import com.smartwash.divination.service.IDivRecordService;
import com.smartwash.divination.vo.MethodVo;
import com.smartwash.divination.vo.RecordDetailVo;
import com.smartwash.divination.vo.RecordVo;
import com.smartwash.divination.vo.UserApiConfigVo;
import com.smartwash.divination.vo.VendorGroupVo;
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
    // 限流：登录态按 userId 计数。LLM 解读按次产生真实 token 成本，QPS 层限流与 div.limit
    // 的每日限额（interpret-per-user-day）互补——日限额防总量刷穿，本层防瞬时高频
    @RateLimit(name = "div-interpret", window = 60, limit = 6, message = "解读请求过于频繁，请稍后再试")
    public SseEmitter interpret(@PathVariable("id") Long id, @RequestBody(required = false) String question) {
        LoginUser user = UserContextHolder.getUser();
        return interpretationService.interpret(id, normalizeQuestion(question), user.getUserId(), false);
    }

    @Operation(summary = "同卦追问", description = "对同一卦例进行追问（SSE 流式）")
    @PostMapping("/records/{id}/followup")
    // 限流：同 interpret，追问同样消耗 LLM token
    @RateLimit(name = "div-followup", window = 60, limit = 6, message = "解读请求过于频繁，请稍后再试")
    public SseEmitter followup(@PathVariable("id") Long id, @RequestBody(required = false) String question) {
        LoginUser user = UserContextHolder.getUser();
        // 空 body / 空白问题按空追问处理（required=false 避免 400；服务端 prompt 组装有空安全路径）
        return interpretationService.interpret(id, normalizeQuestion(question), user.getUserId(), true);
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

    @Operation(summary = "可选模型目录（树形）", description = "一次返回整棵目录树：厂商为一级（含接入点），模型为二级挂在厂商下；密钥由用户自带")
    @GetMapping("/models")
    public Result<java.util.List<VendorGroupVo>> getModels() {
        return Result.ok(modelConfigService.listEnabledTree());
    }

    @Operation(summary = "获取用户模型配置", description = "仅返回掩码，无明文/密文；apiKey 只写不读")
    @GetMapping("/settings/api")
    public Result<UserApiConfigVo> getUserApiConfig() {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(userApiConfigService.getUserConfig(user.getUserId()));
    }

    @Operation(summary = "保存/更换用户模型配置", description = "目录模式（modelConfigId+apiKey）或自定义模式（baseUrl+model+apiKey）；连通性校验 → AES-GCM 加密入库")
    @PutMapping("/settings/api")
    public Result<String> saveUserApiConfig(@RequestBody SaveUserApiFrom from) {
        LoginUser user = UserContextHolder.getUser();
        userApiConfigService.saveUserConfig(user.getUserId(), from);
        return Result.ok("保存成功");
    }

    @Operation(summary = "删除用户模型配置", description = "删除用户自带 API 配置（删除后解读将被拦截，需重新配置）")
    @DeleteMapping("/settings/api")
    public Result<String> deleteUserApiConfig() {
        LoginUser user = UserContextHolder.getUser();
        userApiConfigService.deleteUserConfig(user.getUserId());
        return Result.ok("删除成功");
    }

    @Operation(summary = "连通性测试", description = "不保存，独立限流；目录模式按目录条目接入点试呼，自定义模式按传入接入点试呼")
    @PostMapping("/settings/api/test")
    // 限流：连通性测试独立计数（与方法注释约定一致），按 userId 防高频试呼外部 LLM 网关
    @RateLimit(name = "div-api-test", window = 60, limit = 3, message = "连通性测试过于频繁，请稍后再试")
    public Result<Boolean> testUserApi(@RequestBody SaveUserApiFrom from) {
        // 功能总开关关闭时不允许试呼：返回 ok(false) 与「试呼失败」同语义，客户端无需新增错误分支
        if (!userApiConfigService.isDivUseEnabled()) {
            return Result.ok(false);
        }
        String baseUrl;
        String model;
        if (from.getModelConfigId() != null) {
            // 目录模式：接入点/模型名服务端从目录取
            com.smartwash.divination.entity.DivModelConfig catalog = modelConfigService.getById(from.getModelConfigId());
            if (catalog == null || catalog.getEnabled() == null || catalog.getEnabled() != 1) {
                return Result.ok(false);
            }
            baseUrl = catalog.getBaseUrl();
            model = catalog.getModelId();
        } else {
            baseUrl = from.getBaseUrl();
            model = from.getModel();
        }
        boolean ok = modelConfigService.testConnectivity(baseUrl, from.getApiKey(), model);
        return Result.ok(ok);
    }

    /** 问题参数规范化：空白（含空 body）统一为 null，安全分流与 prompt 组装按空口径处理 */
    private String normalizeQuestion(String question) {
        return (question == null || question.isBlank()) ? null : question;
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
