package com.smartwash.review.from;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 手动建卡请求 DTO（初始 state=Learning, step=0, due=now）。
 */
@Data
public class AddCardFrom {

    @NotBlank(message = "问题不能为空")
    @Size(max = 1000, message = "问题最长 1000 字")
    private String question;

    @NotBlank(message = "答案不能为空")
    @Size(max = 2000, message = "答案最长 2000 字")
    private String answer;
}
