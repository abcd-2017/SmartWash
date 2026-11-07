package com.smartwash.memo.from;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新增速记请求 DTO。
 */
@Data
public class AddMemoFrom {

    /** 手账正文（必填，上限 2000 字符，与 memo.content 列宽一致） */
    @NotBlank(message = "内容不能为空")
    @Size(max = 2000, message = "内容过长（上限2000字符）")
    private String content;

    /** 标签数组（可选）；服务端 trim/去空/去重后以逗号分隔落库 */
    private List<String> tags;
}
