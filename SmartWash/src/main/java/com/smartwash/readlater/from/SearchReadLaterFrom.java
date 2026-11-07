package com.smartwash.readlater.from;

import com.smartwash.from.BaseSearchFrom;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 稍后读分页查询 DTO（继承 BaseSearchFrom 复用 page/size 校验）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchReadLaterFrom extends BaseSearchFrom {

    /** 已读状态筛选：0未读 1已读，空 = 全部 */
    @Min(value = 0, message = "状态参数非法")
    @Max(value = 1, message = "状态参数非法")
    private Integer status;

    /** 单标签筛选（精确命中逗号分隔标签中的项） */
    @Size(max = 32, message = "标签过长")
    private String tag;

    /** 关键词：模糊匹配 url + title + digest */
    @Size(max = 100, message = "关键词过长")
    private String keyword;
}
