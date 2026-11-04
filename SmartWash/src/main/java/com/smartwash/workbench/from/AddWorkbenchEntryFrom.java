package com.smartwash.workbench.from;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增工作台入口请求 DTO。
 * entry_key / icon / color 均为端上白名单或格式约束：非法值一律在入口处拒绝，不落库。
 */
@Data
public class AddWorkbenchEntryFrom {

    /** 业务唯一标识（小写字母开头，2-63 位小写字母/数字/_/-），创建后不可修改 */
    @NotBlank(message = "entry_key 不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_-]{2,63}$", message = "entry_key 须为小写字母开头、2-63位小写字母/数字/_/-")
    private String entryKey;

    /** 入口标题 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 32, message = "标题过长（上限32字符）")
    private String title;

    /** 副标题描述，可空 */
    @Size(max = 128, message = "描述过长（上限128字符）")
    private String description;

    /** 图标 key（端上白名单枚举映射，未知值显示默认图标；gamepad 对应游戏中心、science 对应解压实验舱种子入口） */
    @Pattern(regexp = "^(calendar|search|book|calculate|history|chat|link|science|gamepad|default)$", message = "图标 key 不在白名单内")
    private String icon = "default";

    /** 图标色组 key */
    @Pattern(regexp = "^(green|blue|orange|pink|teal|purple)$", message = "色组 key 须为 green/blue/orange/pink/teal/purple")
    private String color = "green";

    /** 卡片类型 key（端上按类型选组件渲染；tile 两列小卡 / wide 通栏卡 / row 列表行 / notice 公告条，未知值端上兜底 tile） */
    @Pattern(regexp = "^(tile|wide|row|notice)$", message = "卡片类型 key 须为 tile/wide/row/notice")
    private String type = "tile";

    /** 跳转 URI：smartwash://... 或 https://...；notice 公告条可空（纯文案公告），其余类型业务层校验必填 */
    @Size(max = 512, message = "跳转 URI 过长（上限512字符）")
    @Pattern(regexp = "^$|^(smartwash://|https?://)\\S+$", message = "跳转 URI 须以 smartwash:// 或 http(s):// 开头且不含空白")
    private String target;

    /** 角标文案（如 新/热），空则不显示 */
    @Size(max = 16, message = "角标文案过长（上限16字符）")
    private String badge;

    /** 分组标题：端上在与前一项不同时渲染区块标题，空则不分组 */
    @Size(max = 32, message = "分组标题过长（上限32字符）")
    private String sectionTitle;

    /** 配图直链（管理端经上传接口获取），空则纯文字卡片 */
    @Size(max = 512, message = "配图地址过长（上限512字符）")
    @Pattern(regexp = "^$|^https?://\\S+$", message = "配图地址须以 http(s):// 开头且不含空白")
    private String imageUrl;

    /** 1启用 0停用 */
    @Min(value = 0, message = "enabled 只能为 0 或 1")
    @Max(value = 1, message = "enabled 只能为 0 或 1")
    private Integer enabled = 1;

    /** 排序，越小越靠前 */
    @Min(value = 0, message = "排序不能为负数")
    private Integer sort = 100;

    /** 备注 */
    @Size(max = 255, message = "备注过长（上限255字符）")
    private String remark;
}
