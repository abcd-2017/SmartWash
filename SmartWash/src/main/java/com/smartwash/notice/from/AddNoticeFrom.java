package com.smartwash.notice.from;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送站内信请求 DTO（管理端）。
 * 发送人 creatorAdminId 不由前端传入，服务端从登录上下文写入，防伪造。
 */
@Data
public class AddNoticeFrom {

    /** 标题 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题过长（上限100字符）")
    private String title;

    /** 正文 */
    @NotBlank(message = "内容不能为空")
    @Size(max = 2000, message = "内容过长（上限2000字符）")
    private String content;

    /** 类型：SYSTEM/ANNOUNCEMENT（枚举白名单） */
    @NotBlank(message = "类型不能为空")
    @Pattern(regexp = "^(SYSTEM|ANNOUNCEMENT)$", message = "类型仅支持 SYSTEM/ANNOUNCEMENT")
    private String type;

    /** 深链：smartwash://... 或 https://...，可空表示纯文案公告 */
    @Size(max = 512, message = "深链过长（上限512字符）")
    @Pattern(regexp = "^(smartwash://|https?://)\\S*$", message = "深链须以 smartwash:// 或 http(s):// 开头且不含空白")
    private String deepLink;
}
