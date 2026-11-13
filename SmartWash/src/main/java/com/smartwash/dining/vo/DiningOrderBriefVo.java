package com.smartwash.dining.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 加入前预览 brief VO：端上扫码解析出 join_token 后先拉本接口，
 * 用于「是否加入点餐环节：{订单标题}」确认弹窗（spec 用户故事 12）。
 */
@Data
@Accessors(chain = true)
public class DiningOrderBriefVo {

    /** 订单 ID（确认加入后调 join 接口用） */
    private Long orderId;

    /** 订单标题 */
    private String title;

    /** 订单状态：ONGOING / FINISHED（端上可提前提示已结束） */
    private String status;

    /** 发起者标识（脱敏手机号，如 139****9000） */
    private String creatorName;

    /** 本单菜单菜品数 */
    private Long recipeCount;

    /** 当前用户是否已在该单中（已加入时端上可直接进详情页） */
    private Boolean joined;
}
