package com.smartwash.toolbox.delivery.from;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发布文本条目请求 DTO（kind=1 固定走 JSON）。
 */
@Data
public class PublishDeliveryTextFrom {

    /** 文本正文（上限 10 万字符，防单条刷库；文件类条目另受字节上限约束） */
    @NotBlank(message = "文本内容不能为空")
    @Size(max = 100000, message = "文本内容过长（上限10万字符）")
    private String textContent;

    /** 展示标题（可选，不传默认截取文本前 30 字符作摘要） */
    @Size(max = 255, message = "标题过长（上限255字符）")
    private String title;
}
