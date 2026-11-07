package com.smartwash.readlater.from;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新增稍后读请求 DTO。
 */
@Data
public class AddReadLaterFrom {

    /** 收藏链接（必填，仅 https；SSRF 校验在抓取侧执行） */
    @NotBlank(message = "链接不能为空")
    @Size(max = 2048, message = "链接过长（上限2048字符）")
    private String url;

    /** 标签（可选，清洗后逗号分隔落库） */
    @Size(max = 10, message = "标签最多10个")
    private List<String> tags;
}
