package com.smartwash.dining.from;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建点单订单请求 DTO。
 * 菜单来源二选一：显式勾选 recipeIds，或 fromOrderId 复用历史订单的本单菜单（常吃的搭配不用重新勾）。
 */
@Data
public class CreateDiningOrderFrom {

    /** 订单标题（如 周五晚火锅） */
    @NotBlank(message = "订单标题不能为空")
    @Size(max = 64, message = "订单标题过长（上限64字符）")
    private String title;

    /** 本单菜单菜谱 ID 集合（创建后参与者的选菜范围）；与 fromOrderId 二选一 */
    @Size(max = 100, message = "本单菜单菜品数过多（上限100道）")
    private List<Long> recipeIds;

    /** 要复用本单菜单的历史订单 ID（当前用户须是该订单成员）；与 recipeIds 二选一，可选 */
    private Long fromOrderId;
}
