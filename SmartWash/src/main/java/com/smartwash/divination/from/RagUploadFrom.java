package com.smartwash.divination.from;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 上传古籍文档请求 DTO。
 */
@Data
public class RagUploadFrom {

    @NotBlank(message = "书名不能为空")
    private String title;

    @NotBlank(message = "所属书不能为空")
    private String book;

    @NotBlank(message = "适用术数不能为空")
    private String method;

    @NotBlank(message = "内容不能为空")
    private String content;
}
