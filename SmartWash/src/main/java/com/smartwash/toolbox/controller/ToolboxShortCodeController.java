package com.smartwash.toolbox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartwash.common.Result;
import com.smartwash.from.BaseSearchFrom;
import com.smartwash.toolbox.from.AddShortCodeFrom;
import com.smartwash.toolbox.from.SearchShortCodeFrom;
import com.smartwash.toolbox.from.UpdateShortCodeFrom;
import com.smartwash.toolbox.listener.ShortCodeVisitedEvent;
import com.smartwash.toolbox.service.ToolboxShortCodeService;
import com.smartwash.toolbox.vo.ShortCodeStatsVo;
import com.smartwash.toolbox.vo.ShortCodeVo;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

/**
 * 工作台工具箱用户端控制器。
 * 路径前缀 /web/auth/toolbox/short-codes，需 ROLE_USER。
 */
@Tag(name = "工作台-短链", description = "工具箱短码创建/管理与解析接口")
@Slf4j
@RestController
@RequestMapping("/web/auth/toolbox/short-codes")
@RequiredArgsConstructor
public class ToolboxShortCodeController {

    private final ToolboxShortCodeService toolboxShortCodeService;
    private final ApplicationEventPublisher eventPublisher;

    @Operation(summary = "创建短码", description = "target 必填（http/https）；支持自定义别名与过期时间，不传 expireAt = 永久")
    @PostMapping
    public Result<ShortCodeVo> create(@RequestBody @Valid AddShortCodeFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(toolboxShortCodeService.createShortCode(from, user.getUserId()));
    }

    @Operation(summary = "我的短码分页", description = "按创建时间倒序返回当前用户的短码，含完整跳转 URL")
    @GetMapping
    public Result<IPage<ShortCodeVo>> list(SearchShortCodeFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(toolboxShortCodeService.listMyShortCodes(from, user.getUserId()));
    }

    @Operation(summary = "更新短码", description = "活码改向走这里：支持改 target / expireAt，更新后旧码立即跳新目标")
    @PutMapping("/{id}")
    public Result<ShortCodeVo> update(@PathVariable("id") Long id, @RequestBody @Valid UpdateShortCodeFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(toolboxShortCodeService.updateShortCode(id, from, user.getUserId()));
    }

    @Operation(summary = "删除短码", description = "物理删除并清空跳转缓存")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        LoginUser user = UserContextHolder.getUser();
        toolboxShortCodeService.deleteShortCode(id, user.getUserId());
        return Result.ok(null);
    }

    @Operation(summary = "访问明细分页", description = "当前用户短码的点击明细（IP 仅存摘要）")
    @GetMapping("/{id}/stats")
    public Result<IPage<ShortCodeStatsVo>> stats(@PathVariable("id") Long id,
                                                 @Valid BaseSearchFrom from) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(toolboxShortCodeService.pageVisits(id, user.getUserId(), from.getPage(), from.getSize()));
    }

    @Operation(summary = "短码解析", description = "owner 解析（私有码的唯一取值通道）：校验归属后返回目标，App 取 target 自行打开；同样计入点击统计")
    @GetMapping("/resolve")
    public Result<ShortCodeVo> resolve(@RequestParam("code") String code, HttpServletRequest request) {
        LoginUser user = UserContextHolder.getUser();
        ShortCodeVo vo = toolboxShortCodeService.resolveForOwner(code, user.getUserId());
        eventPublisher.publishEvent(new ShortCodeVisitedEvent(code, request));
        return Result.ok(vo);
    }
}
