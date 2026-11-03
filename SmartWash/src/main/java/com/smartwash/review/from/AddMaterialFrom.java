package com.smartwash.review.from;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建复习资料请求 DTO。
 */
@Data
public class AddMaterialFrom {

    @NotBlank(message = "资料标题不能为空")
    @Size(max = 100, message = "资料标题最长 100 字")
    private String title;

    /** 粘贴的原文，可空 */
    @Size(max = 100_000, message = "资料原文过长")
    private String rawText;
}
