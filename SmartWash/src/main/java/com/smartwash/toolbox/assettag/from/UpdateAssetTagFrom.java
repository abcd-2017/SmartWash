package com.smartwash.toolbox.assettag.from;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新物品标签请求 DTO：字段不传即不修改；寻物状态流转裁定在后端（对齐订阅模块惯例）。
 */
@Data
public class UpdateAssetTagFrom {

    /** 物品名称（不传不修改） */
    @Size(max = 64, message = "物品名称过长（上限64字符）")
    private String itemName;

    /** 物品描述（不传不修改） */
    @Size(max = 256, message = "物品描述过长（上限256字符）")
    private String itemDesc;

    /** 联系方式（不传不修改；空串 = 清空联系方式） */
    @Size(max = 64, message = "联系方式过长（上限64字符）")
    private String contact;

    /** 寻物状态：0 正常 / 1 寻物中 / 2 已寻回（不传不修改） */
    @Min(value = 0, message = "寻物状态非法")
    @Max(value = 2, message = "寻物状态非法")
    private Integer status;
}
