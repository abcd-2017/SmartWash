package com.smartwash.toolbox.from;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建短码请求 DTO。
 * 内容类型决定必填面：1 短链必须带 target；7 私密文本必须带 content 与未来 expireAt（均服务端校验）。
 */
@Data
public class AddShortCodeFrom {

    /** 内容类型：1 短链（默认） 7 私密文本（阅后即焚）；其余类型暂不支持端上创建 */
    @Min(value = 1, message = "内容类型非法")
    @Max(value = 8, message = "内容类型非法")
    private Integer contentType;

    /** 目标长链（类型1 必填，必须可解析为 http/https URI，服务端校验） */
    @Size(max = 2048, message = "目标链接过长（上限2048字符）")
    private String target;

    /** 私密文本正文（类型7 必填，≤2000 字符；服务端 AES-256-GCM 加密落库，库中无明文） */
    @Size(max = 2000, message = "私密文本过长（上限2000字符）")
    private String content;

    /** 访问口令（类型7 可选，4-32 位；服务端 BCrypt 落库，永不明文存储） */
    @Size(min = 4, max = 32, message = "口令须为4-32位")
    private String password;

    /** 自定义别名（4-16位字母数字及-_），不传则随机生成 7 位短码 */
    @Pattern(regexp = "^[0-9a-zA-Z-_]{4,16}$", message = "自定义短码须为4-16位字母数字或-_")
    private String customCode;

    /** 过期时刻；类型1 不传 = 永久，类型7 必填（端上提供 1小时/1天/7天 预设） */
    private LocalDateTime expireAt;

    /** 可见性：1公开（默认） 2私有（仅owner可解析）；类型7 忽略传值，恒为公开 */
    @Min(value = 1, message = "可见性参数非法")
    @Max(value = 2, message = "可见性参数非法")
    private Integer isPublic = 1;
}
