package com.smartwash.toolbox.from;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建短码请求 DTO。
 */
@Data
public class AddShortCodeFrom {

    /** 目标长链（必填，必须可解析为 http/https URI） */
    @NotBlank(message = "目标链接不能为空")
    @Size(max = 2048, message = "目标链接过长（上限2048字符）")
    private String target;

    /** 自定义别名（4-16位字母数字及-_），不传则随机生成 7 位短码 */
    @Pattern(regexp = "^[0-9a-zA-Z-_]{4,16}$", message = "自定义短码须为4-16位字母数字或-_")
    private String customCode;

    /** 过期时刻，不传 = 永久（端上提供 永久/7天/30天/自定义 预设） */
    private LocalDateTime expireAt;

    /** 可见性：1公开（默认） 2私有（仅owner可解析） */
    @Min(value = 1, message = "可见性参数非法")
    @Max(value = 2, message = "可见性参数非法")
    private Integer isPublic = 1;
}
