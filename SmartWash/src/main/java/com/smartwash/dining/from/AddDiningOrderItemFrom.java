package com.smartwash.dining.from;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 勾选菜品请求 DTO（共享购物车乐观并发：携带客户端已知 version）。
 */
@Data
public class AddDiningOrderItemFrom {

    /** 要勾选的菜谱 ID（必须在本单菜单范围内） */
    @NotNull(message = "菜谱 ID 不能为空")
    private Long recipeId;

    /** 个人备注（如 不要辣），可空 */
    @Size(max = 128, message = "备注过长（上限128字符）")
    private String note;

    /** 客户端已知的订单 version（与服务端不一致返回业务错误码 409，客户端拉取最新后重放） */
    @NotNull(message = "已知版本号 version 不能为空")
    private Long version;
}
