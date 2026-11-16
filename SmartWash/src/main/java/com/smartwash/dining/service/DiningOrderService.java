package com.smartwash.dining.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.dining.entity.DiningOrder;
import com.smartwash.dining.from.CreateDiningOrderFrom;
import com.smartwash.dining.from.JoinDiningOrderFrom;
import com.smartwash.dining.vo.DiningOrderBriefVo;
import com.smartwash.dining.vo.DiningOrderCreatedVo;
import com.smartwash.dining.vo.DiningOrderDetailVo;
import com.smartwash.dining.vo.DiningOrderFinishVo;
import com.smartwash.dining.vo.DiningOrderListVo;
import com.smartwash.from.BaseSearchFrom;

/**
 * 点单订单服务：创建（含复用历史菜单）/ 我的订单列表 / 详情（version 轮询协议）/
 * 加入前 brief / 加入 / 重置加入凭证。
 * 选菜增改删见 {@link DiningOrderItemService}。
 */
public interface DiningOrderService extends IService<DiningOrder> {

    /**
     * 创建点单订单：校验菜谱上架存在 → 生成 join_token → 创建者为首个成员 → 写入本单菜单。
     * 支持从历史订单复用菜单（fromOrderId，当前用户须为该单成员）。
     */
    DiningOrderCreatedVo create(CreateDiningOrderFrom from, Long userId);

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

    /** 加入前预览 brief：凭 join_token 换订单标题 / 发起者 / 菜品数，用于扫码确认弹窗 */
    DiningOrderBriefVo brief(String joinToken, Long userId);

    /** 加入订单：校验 join_token + ONGOING + 防重复加入（uk_order_user 兜底并发） */
    void join(Long orderId, JoinDiningOrderFrom from, Long userId);

    /** 重置 join_token（仅发起者）：旧二维码全部作废，返回新 token */
    String resetJoinToken(Long orderId, Long userId);

    /**
     * 结束点单（仅发起者、仅 ONGOING）：聚合所有选菜，按菜去重取食材名并集去重 + 按菜视图
     * （菜 → 选了的人 + 备注）写入 shopping_list JSON 快照，status → FINISHED 并记录 finished_at。
     * 重复调用幂等：已 FINISHED 直接返回库内原快照。
     */
    DiningOrderFinishVo finish(Long orderId, Long userId);
}
