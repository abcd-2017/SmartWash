package com.smartwash.dining.from;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改选菜备注请求 DTO（一人一菜一条：改的是自己那条，note 传 null 即清空备注）。
 */
@Data
public class UpdateDiningOrderItemFrom {

    /** 菜谱 ID（本人须已勾选该菜） */
    @NotNull(message = "菜谱 ID 不能为空")
    private Long recipeId;

    /** 新备注；传 null/不传 = 清空备注 */
    @Size(max = 128, message = "备注过长（上限128字符）")
    private String note;

    /** 客户端已知的订单 version（与服务端不一致返回业务错误码 409） */
    @NotNull(message = "已知版本号 version 不能为空")
    private Long version;
}
