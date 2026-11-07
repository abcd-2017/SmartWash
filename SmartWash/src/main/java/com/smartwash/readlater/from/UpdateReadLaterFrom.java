package com.smartwash.readlater.from;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 更新稍后读请求 DTO（patch 语义：传哪个字段改哪个字段，全空则无事发生）。
 */
@Data
public class UpdateReadLaterFrom {

    /** 标签（整体替换，传 null 不修改；空列表清空标签） */
    @Size(max = 10, message = "标签最多10个")
    private List<String> tags;

    /** 已读状态：0未读 1已读，传 null 不修改 */
    @Min(value = 0, message = "状态参数非法")
    @Max(value = 1, message = "状态参数非法")
    private Integer status;
}
