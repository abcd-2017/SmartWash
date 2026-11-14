package com.smartwash.controller.background;

import com.smartwash.common.Result;
import com.smartwash.common.audit.Audited;
import com.smartwash.entity.SysConfig;
import com.smartwash.from.sysConfig.UpdateSysConfigFrom;
import com.smartwash.service.ISysConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统参数配置管理端接口
 */
@Tag(name = "系统参数", description = "系统参数的查询与实时修改（修改落库并立即生效）")
@Slf4j
@RestController
@RequestMapping("/admin/config")
public class SysConfigController {

    @Autowired
    private ISysConfigService sysConfigService;

    @Operation(summary = "查询全部系统参数", description = "参数总量少，一次性返回全量列表")
    @GetMapping("/list")
    public Result<List<SysConfig>> list() {
        return Result.ok(sysConfigService.getAllConfigs());
    }

    @Operation(summary = "更新参数值", description = "按参数 ID 修改参数值与备注，落库后立即生效")
    @Audited(action = "SYS_CONFIG_UPDATE", targetType = "sysConfig", targetIdSpEL = "#form.id")
    @PostMapping("/update")
    public Result<String> update(@RequestBody @Valid UpdateSysConfigFrom form) {
        try {
            boolean ok = sysConfigService.updateValue(form.getId(), form.getConfigValue(), form.getRemark());
            if (!ok) {
                log.warn("更新系统参数被拒绝：参数不存在, id: {}", form.getId());
                return Result.failMsg("参数不存在");
            }
            return Result.ok("修改成功");
        } catch (IllegalArgumentException e) {
            log.warn("更新系统参数被拒绝：值非法, id: {}, reason: {}", form.getId(), e.getMessage());
            return Result.failMsg(e.getMessage());
        }
    }
}
