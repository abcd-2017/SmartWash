package com.smartwash.toolbox.assettag.from;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建物品标签请求 DTO（对齐方案 §5.3）。
 * 标签默认永久（expireAt 不传）；主记录复用短码内核，别名规则同短码。
 */
@Data
public class CreateAssetTagFrom {

    /** 物品名称（必填 ≤64 字符） */
    @NotBlank(message = "物品名称不能为空")
    @Size(max = 64, message = "物品名称过长（上限64字符）")
    private String itemName;

    /** 物品描述（可选 ≤256 字符） */
    @Size(max = 256, message = "物品描述过长（上限256字符）")
    private String itemDesc;

    /** 联系方式（可选 ≤64 字符；服务端 AES-256-GCM 加密落库，仅寻物中状态解密下发） */
    @Size(max = 64, message = "联系方式过长（上限64字符）")
    private String contact;

    /** 自定义别名（可选 4-16 位字母数字及 -_），不传随机生成 */
    @Pattern(regexp = "^[0-9a-zA-Z-_]{4,16}$", message = "自定义短码须为4-16位字母数字或-_")
    private String customCode;
}
