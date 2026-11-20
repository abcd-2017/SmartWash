package com.smartwash.dining.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.dining.from.AddDiningOrderDishFrom;
import com.smartwash.dining.from.CreateDiningOrderFrom;
import com.smartwash.dining.from.JoinDiningOrderFrom;
import com.smartwash.dining.from.SubmitDiningOrderFrom;
import com.smartwash.dining.service.DiningOrderService;
import com.smartwash.dining.stream.DiningOrderStreamer;
import com.smartwash.dining.vo.DiningOrderActiveVo;
import com.smartwash.dining.vo.DiningOrderBriefVo;
import com.smartwash.dining.vo.DiningOrderCreatedVo;
import com.smartwash.dining.vo.DiningOrderDetailVo;
import com.smartwash.dining.vo.DiningOrderFinishVo;
import com.smartwash.dining.vo.DiningOrderListVo;
import com.smartwash.from.BaseSearchFrom;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 吃啥-订单用户端控制器。
 * 路径前缀 /web/auth/dining/orders，需 ROLE_USER（SecurityConfig 的 /web/auth/** 通配统一拦截，无需改权限配置）。
 * 全部接口以当前登录用户为主体（UserContextHolder 注入 userId）。
 * 产品模型：菜单即已选清单，全员共同编辑，发起者一步「提交」→ 订单直接结束。
 * create 创建 DRAFT（标题/菜单可空），submit 提交即 FINISHED 并生成购买清单快照；
 * 状态机只允许 DRAFT→FINISHED，无 ONGOING。
 * 四端联动说明：本次仅用户端接口（Android/鸿蒙消费），管理端不涉及。
 */
@Tag(name = "吃啥-订单用户端", description = "创建草稿/提交点单/草稿加菜/移除菜/放弃草稿/我的订单/详情轮询/加入前brief/加入/重置加入凭证")
@Slf4j
@RestController
@RequestMapping("/web/auth/dining/orders")
@RequiredArgsConstructor
public class WebDiningOrderController {

    private final DiningOrderService diningOrderService;

    private final DiningOrderStreamer streamer;

    @Operation(summary = "创建点单草稿", description = "title/recipeIds 均可空（或 fromOrderId 复用历史菜单，二者二选一）→ "
            + "创建 DRAFT 草稿，创建者自动成为首个成员，joinToken 创建时即生成（草稿即可邀请）；"
            + "菜谱非空时须上架存在；返回 orderId + joinToken 供端上拼二维码")
    @PostMapping("/create")
    public Result<DiningOrderCreatedVo> create(@RequestBody @Valid CreateDiningOrderFrom from) {
        return Result.ok(diningOrderService.create(from, currentUserId()));
    }

    @Operation(summary = "提交点单（提交即结束）", description = "DRAFT→FINISHED；仅发起者；校验标题必填 + 菜单非空；"
            + "聚合本单菜单生成 shopping_list 快照（按菜视图无选菜人），通知全体成员；"
            + "携带客户端已知 version 做乐观锁，冲突返回业务码 409（端上刷新后重试）；重复提交幂等返回原快照")
    @PostMapping("/{id}/submit")
    public Result<DiningOrderFinishVo> submit(@PathVariable("id") Long id,
                                              @RequestBody @Valid SubmitDiningOrderFrom from) {
        return Result.ok(diningOrderService.submit(id, from, currentUserId()));
    }

    @Operation(summary = "草稿加菜", description = "往本单菜单加一道菜（recipeId）；仅 DRAFT 态、全部成员可加；"
            + "菜谱须存在且上架；已存在按幂等成功返回；version 冲突返回 409")
    @PostMapping("/{id}/dishes")
    public Result<Void> addDish(@PathVariable("id") Long id,
                                @RequestBody @Valid AddDiningOrderDishFrom from) {
        diningOrderService.addDish(id, from, currentUserId());
        return Result.ok(null);
    }

    @Operation(summary = "草稿移除菜", description = "从本单菜单移除一道菜；仅 DRAFT 态、全部成员可移除任意菜；"
            + "不存在按幂等成功返回；version 冲突返回 409")
    @DeleteMapping("/{id}/dishes/{recipeId}")
    public Result<Void> removeDish(
            @PathVariable("id") Long id,
            @PathVariable("recipeId") Long recipeId,
            @Parameter(description = "客户端已知的订单 version（乐观并发闸门），缺失按业务失败返回")
            @RequestParam(value = "version", required = false) Long version) {
        diningOrderService.removeDish(id, recipeId, version, currentUserId());
        return Result.ok(null);
    }

    @Operation(summary = "放弃草稿（置已取消）", description = "仅发起者、仅 DRAFT 态；订单状态置 CANCELLED 并写 cancelled_at，"
            + "不再物理删除——参与者仍可在「我的订单」看到该单（灰态「已取消」）；已提交/已取消拒绝")
    @DeleteMapping("/{id}")
    public Result<Void> cancel(@PathVariable("id") Long id) {
        diningOrderService.cancel(id, currentUserId());
        return Result.ok(null);
    }

    @Operation(summary = "退出点单", description = "仅参与者（非发起者）、仅 DRAFT 态、仅成员；删除成员行即脱离该单，"
            + "订单本体保留（发起者可继续编辑/提交/放弃）；已不在单中按幂等成功返回")
    @PostMapping("/{id}/leave")
    public Result<Void> leave(@PathVariable("id") Long id) {
        diningOrderService.leave(id, currentUserId());
        return Result.ok(null);
    }

    @Operation(summary = "我的订单列表（分页）", description = "我发起的 + 我参与的，按创建时间倒序，带 isCreator 标记；"
            + "列表项含 status（DRAFT 草稿 / FINISHED 已结束），草稿带标签展示；"
            + "page 默认 1、size 默认 10 上限 100")
    @GetMapping("/my")
    public Result<Page<DiningOrderListVo>> my(@Valid BaseSearchFrom from) {
        return Result.ok(diningOrderService.pageMy(from, currentUserId()));
    }

    @Operation(summary = "我的进行中点单", description = "当前用户作为发起者、状态为 DRAFT 的唯一订单（无则 data=null）；"
            + "列表 FAB / lite 主 CTA 据此决定进入该单编辑态还是空白创建页")
    @GetMapping("/active")
    public Result<DiningOrderActiveVo> myActive() {
        return Result.ok(diningOrderService.getMyActiveOrder(currentUserId()));
    }

    @Operation(summary = "订单详情（轮询主接口）", description = "点单页约 3s 一次带 knownVersion 轮询："
            + "与服务端 version 一致返回 unchanged=true 轻量响应（仅 id/version/unchanged）；"
            + "不一致或未带 knownVersion 返回全量（status + 本单菜单+选菜记录+参与者+version）。"
            + "端上按 status 切草稿编辑态（DRAFT）/ 已结束结果态（FINISHED）；仅订单成员可查看")
    @GetMapping("/{id}")
    public Result<DiningOrderDetailVo> detail(
            @PathVariable("id") Long id,
            @Parameter(description = "客户端已知的订单 version，可选；不传视为首次拉取（全量）")
            @RequestParam(value = "knownVersion", required = false) Long knownVersion) {
        return Result.ok(diningOrderService.detail(id, knownVersion, currentUserId()));
    }

    @Operation(summary = "订阅订单变更（SSE）", description = "仅订单成员可订阅；写操作（加/移除菜、提交、放弃、退出、加入、重置凭证）"
            + "事务提交后推 event: changed（data={\"orderId\":N}），端上收到信号回拉 GET /{id}?knownVersion= 取最新详情；"
            + "终态同样可订阅以感知状态变化；服务端 30s 心跳防代理断连")
    @GetMapping("/{id}/stream")
    public SseEmitter stream(@PathVariable("id") Long id) {
        diningOrderService.assertReadable(id, currentUserId());
        return streamer.subscribe(id);
    }

    @Operation(summary = "加入前预览 brief", description = "凭二维码里的 join_token 换订单标题/发起者/菜品数，"
            + "用于「是否加入点餐环节」确认弹窗；joined 标记当前用户是否已在单中")
    @GetMapping("/brief/{token}")
    public Result<DiningOrderBriefVo> brief(@PathVariable("token") String token) {
        return Result.ok(diningOrderService.brief(token, currentUserId()));
    }

    @Operation(summary = "加入订单", description = "校验 join_token 匹配 + 订单状态为 DRAFT + 未重复加入"
            + "（凭证无效/已结束/重复加入三类失败均有明确业务提示）")
    @PostMapping("/{id}/join")
    public Result<Void> join(@PathVariable("id") Long id, @RequestBody @Valid JoinDiningOrderFrom from) {
        diningOrderService.join(id, from, currentUserId());
        return Result.ok(null);
    }

    @Operation(summary = "重置加入凭证", description = "仅发起者、仅 DRAFT 态；旧二维码全部作废，返回新 joinToken")
    @PostMapping("/{id}/join-token/reset")
    public Result<String> resetJoinToken(@PathVariable("id") Long id) {
        return Result.ok(diningOrderService.resetJoinToken(id, currentUserId()));
    }

    private Long currentUserId() {
        LoginUser user = UserContextHolder.getUser();
        return user.getUserId();
    }
}
