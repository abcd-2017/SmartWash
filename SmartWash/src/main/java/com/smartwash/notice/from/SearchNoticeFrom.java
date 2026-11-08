package com.smartwash.notice.from;

import com.smartwash.from.BaseSearchFrom;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站内信分页筛选 DTO（管理端列表 + 用户端收件箱共用基类，均继承 BaseSearchFrom）。
 * 管理端可按标题模糊 + 类型筛选；用户端收件箱不传筛选即全量分页。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchNoticeFrom extends BaseSearchFrom {

    /** 标题模糊筛选（管理端） */
    @Size(max = 100, message = "关键词过长（上限100字符）")
    private String title;

    /** 类型筛选：SYSTEM/ANNOUNCEMENT（管理端，可空=全部） */
    @Pattern(regexp = "^(SYSTEM|ANNOUNCEMENT)$", message = "类型仅支持 SYSTEM/ANNOUNCEMENT")
    private String type;
}
