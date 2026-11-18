package com.smartwash.dining.from;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 加菜请求 DTO（草稿期菜单编辑）：往本单菜单加一道菜。
 * 仅 DRAFT 态、全部成员可加；已存在按幂等成功返回（不重复落库、不递增 version）。
 * version 乐观锁：与服务端不一致返回业务错误码 409。
 */
@Data
public class AddDiningOrderDishFrom {

    /** 要加入本单菜单的菜谱 ID（须存在且上架） */
    @NotNull(message = "菜谱 ID 不能为空")
    private Long recipeId;

    /** 客户端已知的订单 version（与服务端不一致返回业务错误码 409，客户端拉取最新后重放） */
    @NotNull(message = "已知版本号 version 不能为空")
    private Long version;
}
