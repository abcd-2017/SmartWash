package com.smartwash.game.from;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 上传游戏请求 DTO（multipart 表单字段，与文件一并提交）。
 * gameKey / icon / color / category 均为白名单或格式约束：非法值一律在入口处拒绝，不落库。
 * gameKey 同时进 MinIO 对象键与转发 URL，格式约束防止路径注入。
 */
@Data
public class UploadGameFrom {

    /** 业务唯一标识（小写字母开头，2-63 位小写字母/数字/_/-），重复上传同 key = 覆盖更新，创建后不可在编辑接口修改 */
    @NotBlank(message = "gameKey 不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_-]{2,63}$", message = "gameKey 须为小写字母开头、2-63位小写字母/数字/_/-")
    private String gameKey;

    /** 游戏名 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 32, message = "标题过长（上限32字符）")
    private String title;

    /** 一句话玩法描述，可空 */
    @Size(max = 128, message = "描述过长（上限128字符）")
    private String description;

    /** 图标 key（游戏域白名单，与 Android :feature:game 图标映射键集对齐；default/未知值显示默认图标） */
    @Pattern(regexp = "^(gamepad|arcade|puzzle|grid|board|word|default)$", message = "图标 key 须为 gamepad/arcade/puzzle/grid/board/word/default")
    private String icon = "default";

    /** 图标色组 key */
    @Pattern(regexp = "^(green|blue|orange|pink|teal|purple)$", message = "色组 key 须为 green/blue/orange/pink/teal/purple")
    private String color = "green";

    /** 分类 */
    @Pattern(regexp = "^(puzzle|arcade|word|board|other)$", message = "分类须为 puzzle/arcade/word/board/other")
    private String category = "puzzle";

    /** 角标文案（新/热），空则不显示 */
    @Size(max = 16, message = "角标文案过长（上限16字符）")
    private String badge;

    /** 排序，越小越靠前 */
    @Min(value = 0, message = "排序不能为负数")
    private Integer sort = 100;

    /** 备注 */
    @Size(max = 255, message = "备注过长（上限255字符）")
    private String remark;
}
