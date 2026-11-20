package com.smartwash.feature.dining.logic

import java.io.IOException

/**
 * 吃啥业务码（信封 `code` 字段，非 HTTP 状态码）——契约源后端
 * `WebDiningOrderItemController` / `DiningOrderItemServiceImpl`。
 *
 * `:common:network` 的 ResponseInterceptor 只特判信封 401（登出）与 201（通用失败转
 * NetworkException），本组码原样穿过拦截器，由 [com.smartwash.feature.dining.repository.DiningRepository]
 * 统一转译为 [DiningVersionConflictException]，绝不触发全局 401 登出语义。
 */
object DiningOrderBusinessCode {

    /** 乐观并发版本冲突（version 条件 UPDATE 影响 0 行；「订单已被他人更新，请刷新后重试」） */
    const val VERSION_CONFLICT = 409

    /**
     * 唯一性冲突：同一发起者已有进行中的 DRAFT 草稿，create 被拒
     * （「你有一个正在编辑的点单「xxx」，请先完成或放弃后再开始」）。
     * 端上入口已先查 active 收敛，该码主要覆盖换端/并发窗口；文案以后端 message 为准。
     */
    const val UNIQUE_ACTIVE_ORDER = 40901
}

/**
 * 版本冲突异常：写操作携带的已知 version 与服务端不一致。
 * 调用方拉取最新详情后按本地意图重放（见 [DiningCartSyncCoordinator]）。
 */
class DiningVersionConflictException(message: String) : IOException(message)
