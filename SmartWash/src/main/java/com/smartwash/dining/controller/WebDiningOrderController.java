package com.smartwash.dining.controller;

import com.smartwash.common.Result;
import com.smartwash.dining.from.CreateDiningOrderFrom;
import com.smartwash.dining.from.JoinDiningOrderFrom;
import com.smartwash.dining.service.DiningOrderService;
import com.smartwash.dining.vo.DiningOrderBriefVo;
import com.smartwash.dining.vo.DiningOrderCreatedVo;
import com.smartwash.dining.vo.DiningOrderDetailVo;
import com.smartwash.dining.vo.DiningOrderFinishVo;
import com.smartwash.dining.vo.DiningOrderListVo;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 家庭点单-订单用户端控制器。
 * 路径前缀 /web/auth/dining/orders，需 ROLE_USER（SecurityConfig 的 /web/auth/** 通配统一拦截，无需改权限配置）。
 * 全部接口以当前登录用户为主体（UserContextHolder 注入 userId）。
 * 四端联动说明：本组为新增接口，不改动任何既有接口；Android/鸿蒙端后续接入（本次不消费）。
 */
@Tag(name = "家庭点单-订单用户端", description = "创建/我的订单/详情轮询/加入前brief/加入/重置加入凭证/结束点单")
@Slf4j
@RestController
@RequestMapping("/web/auth/dining/orders")
@RequiredArgsConstructor
public class WebDiningOrderController {

    private final DiningOrderService diningOrderService;

    @Operation(summary = "创建点单订单", description = "title + recipeIds（或 fromOrderId 复用历史菜单，二者二选一）；"
            + "菜谱须上架存在，创建者自动成为首个成员；返回 orderId + joinToken 供端上拼二维码")
    @PostMapping("/create")
    public Result<DiningOrderCreatedVo> create(@RequestBody @Valid CreateDiningOrderFrom from) {
        return Result.ok(diningOrderService.create(from, currentUserId()));
    }

    @Operation(summary = "我的订单列表", description = "我发起的 + 我参与的，按创建时间倒序，带 isCreator 标记")
    @GetMapping("/my")
    public Result<List<DiningOrderListVo>> my() {
        return Result.ok(diningOrderService.listMy(currentUserId()));
    }

    @Operation(summary = "订单详情（轮询主接口）", description = "点单页约 3s 一次带 knownVersion 轮询："
            + "与服务端 version 一致返回 unchanged=true 轻量响应（仅 id/version/unchanged）；"
            + "不一致或未带 knownVersion 返回全量（本单菜单+选菜记录+参与者+version）。仅订单成员可查看")
    @GetMapping("/{id}")
    public Result<DiningOrderDetailVo> detail(
            @PathVariable("id") Long id,
            @Parameter(description = "客户端已知的订单 version，可选；不传视为首次拉取（全量）")
            @RequestParam(value = "knownVersion", required = false) Long knownVersion) {
        return Result.ok(diningOrderService.detail(id, knownVersion, currentUserId()));
    }

    @Operation(summary = "加入前预览 brief", description = "凭二维码里的 join_token 换订单标题/发起者/菜品数，"
            + "用于「是否加入点餐环节」确认弹窗；joined 标记当前用户是否已在单中")
    @GetMapping("/brief/{token}")
    public Result<DiningOrderBriefVo> brief(@PathVariable("token") String token) {
        return Result.ok(diningOrderService.brief(token, currentUserId()));
    }

    @Operation(summary = "加入订单", description = "校验 join_token 匹配 + 订单进行中 + 未重复加入（三类失败均有明确业务提示）")
    @PostMapping("/{id}/join")
    public Result<Void> join(@PathVariable("id") Long id, @RequestBody @Valid JoinDiningOrderFrom from) {
        diningOrderService.join(id, from, currentUserId());
        return Result.ok(null);
    }

    @Operation(summary = "重置加入凭证", description = "仅发起者；旧二维码全部作废，返回新 joinToken")
    @PostMapping("/{id}/join-token/reset")
    public Result<String> resetJoinToken(@PathVariable("id") Long id) {
        return Result.ok(diningOrderService.resetJoinToken(id, currentUserId()));
    }

    @Operation(summary = "结束点单", description = "仅发起者且仅 ONGOING；聚合所有选菜（按菜去重取食材名并集去重 + "
            + "按菜视图）写入 shopping_list JSON 快照，status → FINISHED 并记录 finished_at；重复调用幂等返回原快照")
    @PostMapping("/{id}/finish")
    public Result<DiningOrderFinishVo> finish(@PathVariable("id") Long id) {
        return Result.ok(diningOrderService.finish(id, currentUserId()));
    }

    private Long currentUserId() {
        LoginUser user = UserContextHolder.getUser();
        return user.getUserId();
    }
}
