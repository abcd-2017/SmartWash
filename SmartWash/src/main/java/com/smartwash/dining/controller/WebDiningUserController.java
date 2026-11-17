package com.smartwash.dining.controller;

import com.smartwash.common.Result;
import com.smartwash.dining.from.DietaryPreferenceFrom;
import com.smartwash.dining.service.DietaryPreferenceService;
import com.smartwash.dining.vo.DietaryPreferenceVo;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 吃啥-用户域设置控制器。
 * 路径前缀 /web/auth/dining，需 ROLE_USER（SecurityConfig /web/auth/** 通配统一拦截）。
 * 当前承载「饮食偏好」（忌口 + 每日目标，契约 §2.1），后续用户域设置项按同一前缀扩充。
 * 全部接口以当前登录用户为主体（UserContextHolder 注入 userId），用户只能读写自己的偏好。
 */
@Tag(name = "吃啥-用户设置", description = "饮食偏好（忌口+目标）（ROLE_USER）")
@Slf4j
@RestController
@RequestMapping("/web/auth/dining")
@RequiredArgsConstructor
public class WebDiningUserController {

    private final DietaryPreferenceService dietaryPreferenceService;

    @Operation(summary = "获取饮食偏好", description = "忌口标签 + 每日热量/钠目标；无记录或字段 null=默认值语义（端上兜底 2000kcal/2000mg）")
    @GetMapping("/dietary-preference")
    public Result<DietaryPreferenceVo> getDietaryPreference() {
        return Result.ok(dietaryPreferenceService.get(currentUserId()));
    }

    @Operation(summary = "保存饮食偏好", description = "整行 upsert；allergens 数组 + 两目标，任一字段 null=恢复默认（allergens null=清空忌口）")
    @PutMapping("/dietary-preference")
    public Result<String> saveDietaryPreference(@RequestBody @Valid DietaryPreferenceFrom from) {
        dietaryPreferenceService.save(currentUserId(), from);
        return Result.ok("保存成功");
    }

    private Long currentUserId() {
        LoginUser user = UserContextHolder.getUser();
        return user.getUserId();
    }
}
