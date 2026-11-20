package com.smartwash.dining.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.dining.entity.DiningOrder;
import com.smartwash.dining.from.AddDiningOrderDishFrom;
import com.smartwash.dining.from.CreateDiningOrderFrom;
import com.smartwash.dining.from.JoinDiningOrderFrom;
import com.smartwash.dining.from.SubmitDiningOrderFrom;
import com.smartwash.dining.vo.DiningOrderActiveVo;
import com.smartwash.dining.vo.DiningOrderBriefVo;
import com.smartwash.dining.vo.DiningOrderCreatedVo;
import com.smartwash.dining.vo.DiningOrderDetailVo;
import com.smartwash.dining.vo.DiningOrderFinishVo;
import com.smartwash.dining.vo.DiningOrderListVo;
import com.smartwash.from.BaseSearchFrom;

/**
 * 点单订单服务：创建草稿 / 我的订单列表 / 详情（version 轮询协议）/
 * 加入前 brief / 加入 / 重置加入凭证 / 提交点单（submit，提交即结束）/ 草稿期加菜与移除菜。
 * 选菜记录（dining_order_item）已停止写入，购买清单直接聚合本单菜单。
 */
public interface DiningOrderService extends IService<DiningOrder> {

    /**
     * 创建点单订单（草稿态）：标题与菜单均可空 → 落一张 DRAFT 单，生成 join_token，创建者为首个成员。
     * 菜单非空时校验菜谱上架存在；支持从历史订单复用菜单（fromOrderId，当前用户须为该单成员）。
     * <p>唯一闸门（本期）：同一发起者同时最多一个进行中的 DRAFT——已存在时拒绝，
     * 返回业务码 {@code 40901}（message 带既有单标题），端上据此提示并可跳该单。
     */
    DiningOrderCreatedVo create(CreateDiningOrderFrom from, Long userId);

    /**
     * 提交点单（submit）：DRAFT→FINISHED（提交即结束）。仅发起者；校验标题必填 + 菜单非空；
     * 聚合本单菜单生成 shopping_list 快照，通知全体成员；返回购买清单快照（复用 DiningOrderFinishVo）。
     * 携带客户端已知 version 做乐观锁，冲突返回业务错误码 409；重复提交幂等返回库内原快照。
     */
    DiningOrderFinishVo submit(Long orderId, SubmitDiningOrderFrom from, Long userId);

    /**
     * 草稿期加菜：仅 DRAFT 态、全部成员可加；菜谱须存在且上架；
     * 已存在按幂等成功返回（不重复落库、不递增 version）；version 冲突返回 409。
     */
    void addDish(Long orderId, AddDiningOrderDishFrom from, Long userId);

    /**
     * 草稿期移除菜：仅 DRAFT 态、全部成员可移除任意菜（拍板②）；
     * 不存在按幂等成功返回（不递增 version）；version 冲突返回 409。
     */
    void removeDish(Long orderId, Long recipeId, Long version, Long userId);

    /**
     * 放弃草稿（cancel）：仅发起者、仅 DRAFT 态；订单状态置 {@code CANCELLED} 并写 cancelled_at，
     * <p>不再物理删除——参与者仍可在「我的订单」看到该单（灰态「已取消」），满足「加入用户也要看到状态」。
     * 与并发 submit 互斥：条件 UPDATE 仅在仍为 DRAFT 时迁移，已被提交则拒绝且不产生中间态。
     */
    void cancel(Long orderId, Long userId);

    /**
     * 退出点单（leave）：仅参与者（非发起者）、仅 DRAFT 态、仅成员；删除成员行即脱离该单，
     * 订单本体保留（发起者可继续编辑/提交/放弃）。幂等：已不在单中直接成功。
     * 发起者请走 {@link #cancel}（提示文案区分）。
     */
    void leave(Long orderId, Long userId);

    /**
     * 我的进行中点单（本期唯一性口径）：当前用户作为发起者、状态为 DRAFT 的订单，取最新一张；
     * 无则返回 {@code null}（端上 FAB/CTA 据此决定进编辑态还是空白创建页）。
     */
    DiningOrderActiveVo getMyActiveOrder(Long userId);

    /**
     * 我的订单分页（我发起的 + 我参与的，按创建时间倒序，带 isCreator 标记）。
     * 分页参数走 {@link BaseSearchFrom}（page 默认 1、size 默认 10 上限 100）。
     */
    Page<DiningOrderListVo> pageMy(BaseSearchFrom from, Long userId);

    /**
     * 订单详情（轮询主接口）：客户端带已知 version（knownVersion），与服务端一致时返回
     * unchanged=true 轻量响应，否则返回全量（本单菜单 + 选菜记录 + 参与者 + version）。
     * 仅订单成员可查看。
     */
    DiningOrderDetailVo detail(Long orderId, Long knownVersion, Long userId);

    /**
     * SSE 订阅资格校验：订单存在且当前用户为成员（与 detail 同口径，不组装载荷）。
     * 终态（FINISHED/CANCELLED）同样允许订阅，便于端上感知状态变化。
     */
    void assertReadable(Long orderId, Long userId);

    /** 加入前预览 brief：凭 join_token 换订单标题 / 发起者 / 菜品数，用于扫码确认弹窗 */
    DiningOrderBriefVo brief(String joinToken, Long userId);

    /** 加入订单：校验 join_token + 状态为 DRAFT + 防重复加入（uk_order_user 兜底并发） */
    void join(Long orderId, JoinDiningOrderFrom from, Long userId);

    /** 重置 join_token（仅发起者、仅 DRAFT）：旧二维码全部作废，返回新 token */
    String resetJoinToken(Long orderId, Long userId);
}
