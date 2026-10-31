package com.smartwash.game.from;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新游戏元数据请求 DTO（PUT /admin/game/{id}，id 取自路径）。
 * 字段不传（null）即不修改；gameKey 为端上与 URL 对齐标识，不可修改（如需换 key 请删除重建）。
 * badge / description / remark 传空字符串表示清空（落库为 NULL）。
 * 上下架不走本接口（专用 CAS 开关接口 POST /{id}/enabled/{0|1}）。
 */
@Data
public class UpdateGameFrom {

    /** 游戏名 */
    @Size(max = 32, message = "标题过长（上限32字符）")
    private String title;

    /** 一句话玩法描述，空字符串=清空 */
    @Size(max = 128, message = "描述过长（上限128字符）")
    private String description;

    /** 图标 key（游戏域白名单，与 Android :feature:game 图标映射键集对齐） */
    @Pattern(regexp = "^(gamepad|arcade|puzzle|grid|board|word|default)$", message = "图标 key 须为 gamepad/arcade/puzzle/grid/board/word/default")
    private String icon;

    /** 图标色组 key */
    @Pattern(regexp = "^(green|blue|orange|pink|teal|purple)$", message = "色组 key 须为 green/blue/orange/pink/teal/purple")
    private String color;

    /** 分类 */
    @Pattern(regexp = "^(puzzle|arcade|word|board|other)$", message = "分类须为 puzzle/arcade/word/board/other")
    private String category;

    /** 角标文案，空字符串=清空 */
    @Size(max = 16, message = "角标文案过长（上限16字符）")
    private String badge;

    /** 排序，越小越靠前 */
    @Min(value = 0, message = "排序不能为负数")
    private Integer sort;

    /** 备注，空字符串=清空 */
    @Size(max = 255, message = "备注过长（上限255字符）")
    private String remark;
}
