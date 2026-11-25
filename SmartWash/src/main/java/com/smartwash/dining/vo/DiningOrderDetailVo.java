package com.smartwash.dining.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 点单订单详情 VO（轮询主接口）。
 * version 轮询协议：客户端带已知 version（knownVersion）调用，
 * 与服务端当前 version 一致 → unchanged=true 轻量响应（仅回 id/version/unchanged，其余字段为 null）；
 * 不一致或未带 → unchanged=false 全量响应（本单菜单 + 选菜记录 + 参与者 + version）。
 */
@Data
@Accessors(chain = true)
public class DiningOrderDetailVo {

    private Long id;

    /** 服务端当前 version（轮询协议基准） */
    private Long version;

    /**
     * 轮询结果标志：true = 内容与客户端已知 version 一致（轻量响应，其余字段为 null）；
     * false = 全量响应。
     */
    private Boolean unchanged;

    private String title;

    /** 订单状态：DRAFT 草稿 / FINISHED 已结束 */
    private String status;

    private Long creatorUserId;

    /** 本单菜单（发起者编排的选菜范围，按加入菜单顺序） */
    private List<MenuRecipe> menu;

    /** 全部选菜记录（共享购物车明细，按勾选顺序） */
    private List<OrderItem> items;

    private List<OrderMember> members;

    /** 当前请求用户是否为本单发起者（端上据此切换提交确认文案：发起者 / 代发起者） */
    private Boolean amICreator;

    private LocalDateTime finishedAt;

    /** 购买清单快照 JSON 文本（submit 提交时写入，草稿期为 null） */
    private String shoppingList;

    /**
     * 加入凭证（join_token）：仅发起者可见，供发起者从订单列表/详情再次生成二维码；
     * 参与者与轻量（unchanged=true）响应一律为 null，避免凭证经详情接口扩散。
     */
    private String joinToken;

    /** 本单菜单条目：菜谱 ID / 名称 / 分类（端上按分类组织左侧分类栏） */
    @Data
    @Accessors(chain = true)
    public static class MenuRecipe {

        private Long recipeId;

        private String name;

        /** 分类 ID，可空 = 未分类 */
        private Long categoryId;
    }

    /** 选菜记录条目：谁选了哪道菜 + 备注 */
    @Data
    @Accessors(chain = true)
    public static class OrderItem {

        private Long recipeId;

        /** 选菜人用户 ID */
        private Long userId;

        /** 个人备注（如 不要辣），可空 */
        private String note;
    }

    @Data
    @Accessors(chain = true)
    public static class OrderMember {

        private Long userId;

        private Boolean isCreator;

        /** 脱敏手机号（如 139****9000），端上作选菜人标识 */
        private String phoneMasked;

        /** 头像 URL（可空，端上展示协作者/在线成员） */
        private String avatar;

        /**
         * 是否在线（Redis 心跳 presence，TTL 20s；端上据此展示在线成员）。
         * 仅全量响应填充；轻量（unchanged=true）响应 members 为 null。
         */
        private Boolean online;

        /**
         * 该成员登记的忌口标签（SEAFOOD/NUT/GLUTEN/DAIRY/EGG/SOY/SPICY），无则空列表。
         * 同单可见，供发起者点菜时兼顾众口；仅全量响应填充。
         */
        private List<String> allergens;
    }
}
